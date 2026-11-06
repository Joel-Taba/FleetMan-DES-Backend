package com.yowyob.fleet.application.service;

import com.yowyob.fleet.application.service.AppNotifier.Level;
import com.yowyob.fleet.domain.model.Incident;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qui reçoit quoi à chaque étape du dossier d'incident (l'auteur de l'action n'est jamais notifié). */
class IncidentNotifierTest {

    record Sent(UUID user, String title, String message, Level level, String event, Map<String, Object> refs) {
    }

    /** AppNotifier sans base : enregistre les notifications effectivement émises. */
    static class RecordingNotifier extends AppNotifier {
        final List<Sent> sent = new ArrayList<>();

        RecordingNotifier() {
            super(null);
        }

        @Override
        public Mono<Void> toUser(UUID userId, UUID actorId, String title, String message, Level level, String event,
                                 Map<String, Object> refs) {
            if (userId == null || userId.equals(actorId)) return Mono.empty();
            sent.add(new Sent(userId, title, message, level, event, refs));
            return Mono.empty();
        }

        @Override
        public Mono<Void> toFleetManager(UUID fleetId, UUID actorId, String title, String message, Level level, String event,
                                         Map<String, Object> refs) {
            return toUser(MANAGER, actorId, title, message, level, event, refs);
        }
    }

    static final UUID MANAGER = UUID.randomUUID();
    static final UUID HANDLER = UUID.randomUUID();
    static final UUID DRIVER = UUID.randomUUID();

    private RecordingNotifier app;
    private IncidentNotifier.Parties parties;
    private boolean recentlyNotified;
    private IncidentNotifier notifier;
    private Incident incident;

    @BeforeEach
    void setUp() {
        app = new RecordingNotifier();
        parties = new IncidentNotifier.Parties(MANAGER, null, DRIVER, DRIVER, true, null);
        recentlyNotified = false;
        notifier = new IncidentNotifier(app, null) {
            @Override
            public Mono<Parties> parties(UUID incidentId) {
                return Mono.just(parties);
            }

            @Override
            Mono<Boolean> recentlyNotified(UUID userId, UUID incidentId) {
                return Mono.just(recentlyNotified);
            }
        };
        incident = new Incident(UUID.randomUUID(), Incident.Type.ACCIDENT, "choc arrière", Incident.Severity.CRITICAL,
                LocalDateTime.now(), null, BigDecimal.ZERO, "Bella", UUID.randomUUID(), "LT-892-CE", DRIVER, "Bella Ngo");
    }

    private List<Sent> to(UUID user) {
        return app.sent.stream().filter(s -> s.user().equals(user)).toList();
    }

    @Test
    void driverDeclaresCriticalIncident_managerWarnedImmediately_driverNotNotified() {
        incident.setVehicleDrivable(false);

        StepVerifier.create(notifier.declared(incident, DRIVER, " Véhicule immobilisé jusqu'à la résolution de l'incident."))
                .verifyComplete();

        assertEquals(1, app.sent.size());
        Sent s = to(MANAGER).get(0);
        assertEquals(IncidentNotifier.DECLARED, s.event());
        assertEquals(Level.WARNING, s.level());
        assertEquals("Incident grave signalé", s.title());
        assertEquals(incident.getId().toString(), s.refs().get("incidentId"));
        assertTrue(s.message().contains("ne peut plus rouler"));
        assertTrue(s.message().contains("Bella Ngo"));
    }

    @Test
    void managerDeclaresForDriver_driverNotified_managerNot() {
        parties = new IncidentNotifier.Parties(MANAGER, null, DRIVER, MANAGER, false, null);
        incident.setSeverity(Incident.Severity.LOW);

        StepVerifier.create(notifier.declared(incident, MANAGER, "")).verifyComplete();

        assertTrue(to(MANAGER).isEmpty());
        Sent s = to(DRIVER).get(0);
        assertEquals(IncidentNotifier.DECLARED, s.event());
        assertEquals(Level.INFO, s.level());
        assertEquals("Incident déclaré vous concernant", s.title());
    }

    @Test
    void acknowledgedByManager_driverSeesStatus_handlerAssigned() {
        parties = new IncidentNotifier.Parties(MANAGER, HANDLER, DRIVER, DRIVER, true, null);

        StepVerifier.create(notifier.statusChanged(incident, Incident.Status.REPORTED, Incident.Status.ACKNOWLEDGED, MANAGER, null)
                .then(notifier.assigned(incident, HANDLER, MANAGER))).verifyComplete();

        assertTrue(to(MANAGER).isEmpty());
        Sent driver = to(DRIVER).get(0);
        assertEquals(IncidentNotifier.STATUS_CHANGED, driver.event());
        assertEquals("ACKNOWLEDGED", driver.refs().get("status"));
        assertEquals("Incident pris en charge", driver.title());
        assertTrue(to(HANDLER).stream().anyMatch(s -> s.event().equals(IncidentNotifier.ASSIGNED)));
    }

    @Test
    void cancellationCarriesTheReason() {
        StepVerifier.create(notifier.statusChanged(incident, Incident.Status.ACKNOWLEDGED, Incident.Status.CANCELLED, MANAGER,
                "Doublon")).verifyComplete();

        Sent driver = to(DRIVER).get(0);
        assertEquals(Level.WARNING, driver.level());
        assertTrue(driver.message().endsWith("Motif : Doublon"));
    }

    @Test
    void reopenIsNamedAsSuch() {
        assertEquals("Incident rouvert", IncidentNotifier.statusTitle(Incident.Status.RESOLVED, Incident.Status.UNDER_INVESTIGATION));
        assertEquals("Incident en traitement", IncidentNotifier.statusTitle(Incident.Status.ACKNOWLEDGED, Incident.Status.UNDER_INVESTIGATION));
    }

    @Test
    void infoRequest_goesToDriver_andReplyGoesBackToManagerAndHandler() {
        parties = new IncidentNotifier.Parties(MANAGER, HANDLER, DRIVER, DRIVER, true, null);

        StepVerifier.create(notifier.staffMessage(incident, MANAGER, true, "Photo du constat ?")).verifyComplete();
        Sent request = to(DRIVER).get(0);
        assertEquals(IncidentNotifier.INFO_REQUESTED, request.event());
        assertEquals(Level.WARNING, request.level());

        StepVerifier.create(notifier.driverMessage(incident, DRIVER, true, "Envoyée")).verifyComplete();
        assertEquals(IncidentNotifier.DRIVER_REPLY, to(MANAGER).get(0).event());
        assertEquals("Réponse du chauffeur", to(MANAGER).get(0).title());
        assertEquals(IncidentNotifier.DRIVER_REPLY, to(HANDLER).get(0).event());
        assertEquals(1, to(DRIVER).size());
    }

    @Test
    void driverPhotos_notifyManagerOnce_whileUnread() {
        StepVerifier.create(notifier.attachmentAdded(incident, DRIVER, true, "photo.jpg")).verifyComplete();
        assertEquals(IncidentNotifier.ATTACHMENT_ADDED, to(MANAGER).get(0).event());

        recentlyNotified = true;
        StepVerifier.create(notifier.attachmentAdded(incident, DRIVER, true, "photo2.jpg")).verifyComplete();
        assertEquals(1, to(MANAGER).size());
    }

    @Test
    void managerAttachment_notifiesDriver() {
        StepVerifier.create(notifier.attachmentAdded(incident, MANAGER, false, "devis.pdf")).verifyComplete();
        assertTrue(to(MANAGER).isEmpty());
        assertEquals("Pièce ajoutée à votre incident", to(DRIVER).get(0).title());
    }

    @Test
    void tripInterruption_skipsTheDeclaringDriver_resumeReachesDriverAndManager() {
        UUID tripId = UUID.randomUUID();
        StepVerifier.create(notifier.tripInterrupted(incident, tripId, "TRP-1", DRIVER, DRIVER)).verifyComplete();
        assertTrue(app.sent.isEmpty());

        StepVerifier.create(notifier.tripInterrupted(incident, tripId, "TRP-1", DRIVER, MANAGER)).verifyComplete();
        Sent interrupted = to(DRIVER).get(0);
        assertEquals(IncidentNotifier.TRIP_INTERRUPTED, interrupted.event());
        assertEquals(tripId.toString(), interrupted.refs().get("tripId"));

        StepVerifier.create(notifier.tripResumed(incident, tripId, "TRP-1", DRIVER, UUID.randomUUID(), null)).verifyComplete();
        assertTrue(to(DRIVER).stream().anyMatch(s -> s.event().equals(IncidentNotifier.TRIP_RESUMED)));
        assertTrue(to(MANAGER).stream().anyMatch(s -> s.event().equals(IncidentNotifier.TRIP_RESUMED)));
    }

    @Test
    void pendingInfoRequest_isAnsweredByDriverMessageOrPhotoOnly() {
        LocalDateTime t = LocalDateTime.now();
        var request = new IncidentCaseService.TimelineEvent(t, "INFO_REQUESTED", null, null, "Dupont", "Photo ?", false);
        var managerComment = new IncidentCaseService.TimelineEvent(t.plusMinutes(1), "COMMENT", null, null, "Dupont", "Merci", false);
        var driverPhoto = new IncidentCaseService.TimelineEvent(t.plusMinutes(2), "ATTACHMENT_ADDED", null, null, "Bella", "a.jpg", true);
        var driverReply = new IncidentCaseService.TimelineEvent(t.plusMinutes(2), "INFO_PROVIDED", null, null, "Bella", "Voilà", true);

        assertEquals("Photo ?", IncidentCaseService.pendingInfoRequest(List.of(request, managerComment)));
        assertNull(IncidentCaseService.pendingInfoRequest(List.of(request, driverPhoto)));
        assertNull(IncidentCaseService.pendingInfoRequest(List.of(request, driverReply)));
        assertNull(IncidentCaseService.pendingInfoRequest(List.of(managerComment)));
    }
}
