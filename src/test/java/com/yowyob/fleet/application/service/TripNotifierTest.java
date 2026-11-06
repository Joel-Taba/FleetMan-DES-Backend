package com.yowyob.fleet.application.service;

import com.yowyob.fleet.application.service.AppNotifier.Level;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripMissionSubmissionEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripStopEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.VehicleLocalEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripStopR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.VehicleLocalR2dbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Destinataires, codes d'événement et références des notifications de trajet. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripNotifierTest {

    @Mock AppNotifier app;
    @Mock VehicleLocalR2dbcRepository vehicles;
    @Mock TripStopR2dbcRepository stops;

    private TripNotifier notifier;
    private TripEntity trip;
    private final UUID manager = UUID.randomUUID();
    private final UUID driver = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notifier = new TripNotifier(app, vehicles, stops);
        trip = new TripEntity();
        trip.setId(UUID.randomUUID());
        trip.setTripCode("TRP-2026-0042");
        trip.setDriverId(driver);
        trip.setFleetId(UUID.randomUUID());
        trip.setVehicleId(UUID.randomUUID());
        trip.setStatus("SCHEDULED");
        trip.setStartDate(LocalDate.of(2026, 11, 5));
        trip.setStartTime(LocalTime.of(8, 0));
        trip.setScheduledReturnDatetime(LocalDateTime.of(2026, 11, 5, 17, 30));
        trip.setMissionObject("Livraison ciment");
        VehicleLocalEntity vehicle = new VehicleLocalEntity();
        vehicle.setLicensePlate("LT-892-CE");
        when(vehicles.findById(trip.getVehicleId())).thenReturn(Mono.just(vehicle));
        when(stops.findAllByTripIdOrderBySequence(trip.getId()))
                .thenReturn(Flux.just(new TripStopEntity(), new TripStopEntity(), new TripStopEntity()));
        when(app.toUser(any(), any(), anyString(), anyString(), any(), anyString(), any())).thenReturn(Mono.empty());
        when(app.toFleetManager(any(), any(), anyString(), anyString(), any(), anyString(), any())).thenReturn(Mono.empty());
    }

    @Test
    void assigned_tellsTheDriverEverythingNeeded() {
        StepVerifier.create(notifier.assigned(trip, manager)).verifyComplete();

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(app).toUser(eq(driver), eq(manager), eq("Nouveau trajet assigné"), message.capture(), eq(Level.INFO),
                eq("TRIP_ASSIGNED"), eq(Map.of("tripId", trip.getId().toString())));
        assertTrue(message.getValue().contains("TRP-2026-0042"));
        assertTrue(message.getValue().contains("05/11/2026 à 08:00"));
        assertTrue(message.getValue().contains("3 arrêt(s)"));
        assertTrue(message.getValue().contains("LT-892-CE"));
        assertTrue(message.getValue().contains("Livraison ciment"));
        assertFalse(message.getValue().contains("—"));
    }

    @Test
    void unassigned_warnsTheFormerDriver() {
        UUID former = UUID.randomUUID();
        StepVerifier.create(notifier.unassigned(trip, former, manager)).verifyComplete();
        verify(app).toUser(eq(former), eq(manager), eq("Trajet retiré"), anyString(), eq(Level.WARNING),
                eq("TRIP_UNASSIGNED"), eq(Map.of("tripId", trip.getId().toString())));
    }

    @Test
    void updated_summarisesChanges_andSkipsWhenNothingChanged() {
        StepVerifier.create(notifier.updated(trip, manager, List.of())).verifyComplete();
        verifyNoInteractions(app);

        StepVerifier.create(notifier.updated(trip, manager, List.of("départ déplacé au 05/11/2026 à 09:00"))).verifyComplete();
        verify(app).toUser(eq(driver), eq(manager), eq("Trajet modifié"),
                eq("Trajet TRP-2026-0042 : départ déplacé au 05/11/2026 à 09:00."), eq(Level.INFO),
                eq("TRIP_UPDATED"), eq(Map.of("tripId", trip.getId().toString())));
    }

    @Test
    void cancelled_notifiesDriverAndFleetManagerWithReason() {
        StepVerifier.create(notifier.cancelled(trip, manager, "Client fermé")).verifyComplete();
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(app).toUser(eq(driver), eq(manager), eq("Trajet annulé"), message.capture(), eq(Level.WARNING),
                eq("TRIP_CANCELLED"), eq(Map.of("tripId", trip.getId().toString())));
        assertTrue(message.getValue().contains("Motif : Client fermé."));
        // Le gestionnaire auteur est écarté par AppNotifier ; un admin qui annule le prévient.
        verify(app).toFleetManager(eq(trip.getFleetId()), eq(manager), eq("Trajet annulé"), anyString(),
                eq(Level.WARNING), eq("TRIP_CANCELLED"), any());
    }

    @Test
    void deleted_onlyForPlannedTrips_andWithoutTripReference() {
        trip.setStatus("CANCELLED");
        StepVerifier.create(notifier.deleted(trip, manager)).verifyComplete();
        verifyNoInteractions(app);

        trip.setStatus("SCHEDULED");
        StepVerifier.create(notifier.deleted(trip, manager)).verifyComplete();
        verify(app).toUser(eq(driver), eq(manager), eq("Trajet supprimé"), anyString(), eq(Level.WARNING),
                eq("TRIP_DELETED"), eq(Map.of()));
    }

    @Test
    void departed_makesItTheDriversActiveTrip() {
        StepVerifier.create(notifier.departed(trip, manager)).verifyComplete();
        verify(app).toUser(eq(driver), eq(manager), eq("Départ enregistré"), anyString(), eq(Level.SUCCESS),
                eq("TRIP_DEPARTED"), eq(Map.of("tripId", trip.getId().toString())));
    }

    @Test
    void missed_warnsManagerAndDriver_withoutActor() {
        StepVerifier.create(notifier.missed(trip.getId(), trip.getTripCode(), trip.getFleetId(), driver, 2)).verifyComplete();
        Map<String, Object> refs = Map.of("tripId", trip.getId().toString());
        verify(app).toFleetManager(eq(trip.getFleetId()), eq(null), eq("Trajet manqué"), anyString(),
                eq(Level.WARNING), eq("TRIP_MISSED"), eq(refs));
        verify(app).toUser(eq(driver), eq(null), eq("Trajet manqué"), anyString(),
                eq(Level.WARNING), eq("TRIP_MISSED"), eq(refs));
    }

    @Test
    void stopEvents_carryTripAndStopIds() {
        TripStopEntity stop = stop("FAILED");
        stop.setFailureReason("ABSENT");
        stop.setFailureNotes("Portail fermé");
        StepVerifier.create(notifier.stopFailed(trip, stop, driver)).verifyComplete();
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        Map<String, Object> refs = Map.of("tripId", trip.getId().toString(), "stopId", stop.getId().toString());
        verify(app).toFleetManager(eq(trip.getFleetId()), eq(driver), eq("Arrêt non servi"), message.capture(),
                eq(Level.WARNING), eq("TRIP_STOP_FAILED"), eq(refs));
        assertTrue(message.getValue().contains("client absent"));
        assertTrue(message.getValue().contains("Portail fermé"));

        StepVerifier.create(notifier.lateArrival(trip, stop, 25, driver)).verifyComplete();
        verify(app).toFleetManager(eq(trip.getFleetId()), eq(driver), eq("Arrivée en retard"), anyString(),
                eq(Level.WARNING), eq("STOP_LATE_ARRIVAL"), eq(refs));

        StepVerifier.create(notifier.stopSkipped(trip, stop, "Client annulé", manager)).verifyComplete();
        verify(app).toUser(eq(driver), eq(manager), eq("Arrêt retiré de la tournée"), anyString(),
                eq(Level.WARNING), eq("STOP_SKIPPED"), eq(refs));
    }

    @Test
    void stopAdded_enRoute_isAWarning() {
        trip.setStatus("DEPARTED");
        TripStopEntity stop = stop("PENDING");
        StepVerifier.create(notifier.stopAdded(trip, stop, manager)).verifyComplete();
        verify(app).toUser(eq(driver), eq(manager), eq("Nouvel arrêt sur votre tournée"), anyString(),
                eq(Level.WARNING), eq("STOP_ADDED"), any());
    }

    @Test
    void tourDone_isAWarningWhenStopsWereNotServed() {
        StepVerifier.create(notifier.tourDone(trip, 2, 3, driver)).verifyComplete();
        verify(app).toFleetManager(eq(trip.getFleetId()), eq(driver), eq("Tournée terminée"),
                eq("Trajet TRP-2026-0042 : 2/3 arrêt(s) servi(s), retour à la base en cours."),
                eq(Level.WARNING), eq("TRIP_TOUR_DONE"), eq(Map.of("tripId", trip.getId().toString())));
    }

    @Test
    void submissions_goToManagerThenBackToTheDriver() {
        TripMissionSubmissionEntity sub = new TripMissionSubmissionEntity();
        sub.setId(UUID.randomUUID());
        sub.setSubmittedBy(driver);
        sub.setChangeType("ADD");
        sub.setDescription("Sacs de ciment");
        sub.setQuantity(4);
        Map<String, Object> refs = Map.of("tripId", trip.getId().toString(), "submissionId", sub.getId().toString());

        StepVerifier.create(notifier.submissionCreated(trip, sub, driver)).verifyComplete();
        verify(app).toFleetManager(eq(trip.getFleetId()), eq(driver), eq("Changement de chargement à valider"), anyString(),
                eq(Level.INFO), eq("SUBMISSION_CREATED"), eq(refs));

        StepVerifier.create(notifier.submissionReviewed(trip, sub, true, manager)).verifyComplete();
        verify(app).toUser(eq(driver), eq(manager), eq("Changement de chargement validé"), anyString(),
                eq(Level.SUCCESS), eq("SUBMISSION_APPROVED"), eq(refs));

        sub.setRejectionReason("Non prévu au contrat");
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        StepVerifier.create(notifier.submissionReviewed(trip, sub, false, manager)).verifyComplete();
        verify(app).toUser(eq(driver), eq(manager), eq("Changement de chargement refusé"), message.capture(),
                eq(Level.WARNING), eq("SUBMISSION_REJECTED"), eq(refs));
        assertTrue(message.getValue().contains("Motif : Non prévu au contrat."));
    }

    @Test
    void when_formatsFrenchDates() {
        assertEquals("05/11/2026 à 08:00", TripNotifier.when(LocalDate.of(2026, 11, 5), LocalTime.of(8, 0)));
        assertEquals("-", TripNotifier.when((LocalDateTime) null));
        verify(app, never()).toUser(any(), any(), anyString(), anyString(), any(), anyString(), any());
    }

    private TripStopEntity stop(String status) {
        TripStopEntity stop = new TripStopEntity();
        stop.setId(UUID.randomUUID());
        stop.setTripId(trip.getId());
        stop.setSequence(2);
        stop.setStopType("DELIVERY");
        stop.setLocation("Marché Mokolo");
        stop.setStatus(status);
        return stop;
    }
}
