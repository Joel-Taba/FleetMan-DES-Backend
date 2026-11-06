package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.TripException;
import com.yowyob.fleet.domain.model.TripStop;
import com.yowyob.fleet.domain.model.TripStopRules;
import com.yowyob.fleet.domain.ports.in.ManageTripUseCase.TripDetailInput;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripDetailEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.TripStopEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripDetailR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripR2dbcRepository;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.TripStopR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tournée multi-arrêts : plan (arrêts ordonnés, manifeste par arrêt) et exécution sur le
 * terrain (arrivée, livraison avec réceptionnaire et preuve, échec motivé, arrêt passé). Quand
 * tous les arrêts sont clos, le trajet passe « livraisons terminées » (ARRIVED) en attendant
 * son retour, enregistré au dépôt par le gestionnaire.
 */
@Service
@RequiredArgsConstructor
public class TripStopService {

    /** Ligne traitée à un arrêt : quantité livrée ou ramassée, et motif d'un écart. */
    public record HandledLine(UUID detailId, Integer quantity, String shortageReason) {}

    public record CompleteCommand(LocalDateTime arrivedAt, LocalDateTime completedAt, String recipientName,
                                  String proofUrl, String notes, List<HandledLine> lines) {}

    public record NewStopCommand(TripStopRules.StopInput stop, Integer position, List<TripDetailInput> lines) {}

    private static final String FILE_PREFIX = "/api/v1/files/";

    private final TripStopR2dbcRepository stops;
    private final TripDetailR2dbcRepository details;
    private final TripR2dbcRepository trips;
    private final AccessScopeService accessScope;
    private final DatabaseClient db;
    private final TripNotifier notifier;

    // ── Plan ─────────────────────────────────────────────────────────────────

    /** Arrêts et manifeste d'un trajet qui vient d'être créé (plan déjà validé). */
    public Mono<Void> persistPlan(TripEntity trip, List<TripStopRules.StopInput> plan, List<TripDetailInput> lines) {
        List<TripStopEntity> created = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
            created.add(newStop(trip, plan.get(i), i + 1));
        }
        List<TripDetailEntity> manifest = new ArrayList<>();
        List<TripDetailInput> inputs = lines != null ? lines : List.of();
        for (int i = 0; i < inputs.size(); i++) {
            TripDetailInput line = inputs.get(i);
            Integer index = line.stopIndex();
            if (index != null && (index < 0 || index >= created.size())) {
                return Mono.error(TripException.invalidStop("Une ligne du manifeste vise un arrêt inexistant."));
            }
            UUID stopId = created.get(index != null ? index : created.size() - 1).getId();
            manifest.add(newLine(trip.getId(), stopId, line, i));
        }
        return stops.saveAll(created).then(details.saveAll(manifest).then()).then(syncDestination(trip.getId()));
    }

    public Mono<List<TripStop>> stopsOf(UUID tripId) {
        return stops.findAllByTripIdOrderBySequence(tripId).map(TripStopService::toDomain).collectList();
    }

    public Flux<TripStop> list(UUID tripId, UUID actorId) {
        return tripFor(tripId, actorId, false).thenMany(Flux.defer(() ->
                stops.findAllByTripIdOrderBySequence(tripId).map(TripStopService::toDomain)));
    }

    /**
     * Ajoute un arrêt (avant le départ ou en route, ex. ramassage urgent). Un ajout après la fin
     * des livraisons rouvre la tournée.
     */
    @Transactional
    public Mono<TripStop> add(UUID tripId, UUID actorId, NewStopCommand cmd) {
        TripStopRules.validateStop(cmd.stop());
        return tripFor(tripId, actorId, true).flatMap(trip -> {
            if (!isOpen(trip.getStatus())) {
                return Mono.error(TripException.stopsNotEditable());
            }
            if ("SCHEDULED".equals(trip.getStatus())) {
                TripStopRules.validatePlannedArrival(cmd.stop().plannedArrival(),
                        ResourceAvailabilityService.slotStart(trip.getStartDate(), trip.getStartTime()),
                        trip.getScheduledReturnDatetime());
            }
            return stops.findAllByTripIdOrderBySequence(tripId).collectList().flatMap(existing -> {
                if (existing.size() >= TripStopRules.MAX_STOPS) {
                    return Mono.error(TripException.invalidStop("Une tournée compte au plus " + TripStopRules.MAX_STOPS + " arrêts."));
                }
                // En route, un nouvel arrêt se place après les arrêts déjà clos.
                int closed = (int) existing.stream().filter(s -> TripStopRules.isTerminal(s.getStatus())).count();
                int requested = cmd.position() != null ? cmd.position() : existing.size();
                int position = Math.max(Math.min(requested, existing.size()), "SCHEDULED".equals(trip.getStatus()) ? 0 : closed);
                TripStopEntity stop = newStop(trip, cmd.stop(), position + 1);
                List<TripStopEntity> shifted = new ArrayList<>();
                for (TripStopEntity s : existing) {
                    if (s.getSequence() > position) {
                        s.setSequence(s.getSequence() + 1);
                        s.setUpdatedAt(LocalDateTime.now());
                        s.setNew(false);
                        shifted.add(s);
                    }
                }
                List<TripDetailInput> lines = cmd.lines() != null ? cmd.lines() : List.of();
                List<TripDetailEntity> manifest = new ArrayList<>();
                for (int i = 0; i < lines.size(); i++) {
                    TripDetailEntity line = newLine(tripId, stop.getId(), lines.get(i), 100 + i);
                    if (!"SCHEDULED".equals(trip.getStatus())) {
                        // Chargé en route : la quantité prévue est celle qui part avec le chauffeur.
                        line.setDepartureQuantity(line.getQuantity());
                    }
                    manifest.add(line);
                }
                Mono<Void> reopen = "ARRIVED".equals(trip.getStatus())
                        ? db.sql("UPDATE fleet.trips SET status = 'DEPARTED', arrived_at = NULL, updated_at = NOW() WHERE id = :id")
                                .bind("id", tripId).fetch().rowsUpdated().then()
                        : Mono.empty();
                return stops.saveAll(shifted).then(stops.save(stop))
                        .then(details.saveAll(manifest).then())
                        .then(reopen)
                        .then(syncDestination(tripId))
                        .then(Mono.defer(() -> stops.findById(stop.getId())))
                        .flatMap(saved -> notifier.stopAdded(trip, saved, actorId).thenReturn(toDomain(saved)));
            });
        });
    }

    /** Coordonnées, contact, consignes ou heure prévue d'un arrêt encore en attente. */
    @Transactional
    public Mono<TripStop> update(UUID tripId, UUID stopId, UUID actorId, TripStopRules.StopInput input) {
        TripStopRules.validateStop(input);
        return tripFor(tripId, actorId, true).flatMap(trip -> {
            if (!isOpen(trip.getStatus())) {
                return Mono.error(TripException.stopsNotEditable());
            }
            if ("SCHEDULED".equals(trip.getStatus())) {
                TripStopRules.validatePlannedArrival(input.plannedArrival(),
                        ResourceAvailabilityService.slotStart(trip.getStartDate(), trip.getStartTime()),
                        trip.getScheduledReturnDatetime());
            }
            return owned(tripId, stopId).flatMap(stop -> {
                if (!"PENDING".equals(stop.getStatus())) {
                    return Mono.error(TripException.stopNotServable(stop.getStatus()));
                }
                apply(stop, input);
                stop.setNew(false);
                return stops.save(stop)
                        .flatMap(saved -> notifier.stopUpdated(trip, saved, actorId).thenReturn(saved));
            });
        }).flatMap(saved -> syncDestination(tripId).thenReturn(toDomain(saved)));
    }

    /** Retire un arrêt avant le départ, avec ses lignes de manifeste. */
    @Transactional
    public Mono<Void> delete(UUID tripId, UUID stopId, UUID actorId) {
        return tripFor(tripId, actorId, true).flatMap(trip -> {
            if (!"SCHEDULED".equals(trip.getStatus())) {
                return Mono.error(TripException.stopsReorderOnlyPlanned());
            }
            return stops.findAllByTripIdOrderBySequence(tripId).collectList().flatMap(existing -> {
                TripStopEntity target = existing.stream().filter(s -> s.getId().equals(stopId)).findFirst().orElse(null);
                if (target == null) {
                    return Mono.error(TripException.stopNotFound());
                }
                if (existing.size() <= 1) {
                    return Mono.error(TripException.lastStopRequired());
                }
                List<UUID> order = existing.stream().map(TripStopEntity::getId).filter(id -> !id.equals(stopId)).toList();
                return db.sql("DELETE FROM fleet.trip_details WHERE stop_id = :stop").bind("stop", stopId).fetch().rowsUpdated()
                        .then(stops.delete(target))
                        .then(resequence(tripId, order))
                        .then(notifier.updated(trip, actorId, List.of("arrêt « " + target.getLocation() + " » retiré de la tournée")));
            });
        });
    }

    /** Nouvel ordre de passage, avant le départ : une permutation des arrêts du trajet. */
    @Transactional
    public Mono<List<TripStop>> reorder(UUID tripId, UUID actorId, List<UUID> order) {
        return tripFor(tripId, actorId, true).flatMap(trip -> {
            if (!"SCHEDULED".equals(trip.getStatus())) {
                return Mono.error(TripException.stopsReorderOnlyPlanned());
            }
            return stops.findAllByTripIdOrderBySequence(tripId).collectList().flatMap(existing -> {
                var ids = existing.stream().map(TripStopEntity::getId).toList();
                if (order == null || order.size() != ids.size() || !new HashSet<>(order).equals(new HashSet<>(ids))) {
                    return Mono.error(TripException.invalidStop("Le nouvel ordre doit reprendre chaque arrêt une seule fois."));
                }
                Map<UUID, TripStopEntity> byId = new java.util.HashMap<>();
                existing.forEach(s -> byId.put(s.getId(), s));
                LocalDateTime previous = null;
                for (UUID id : order) {
                    LocalDateTime planned = byId.get(id).getPlannedArrival();
                    if (planned != null) {
                        if (previous != null && planned.isBefore(previous)) {
                            return Mono.error(TripException.invalidStop("Les heures prévues des arrêts doivent suivre l'ordre de passage."));
                        }
                        previous = planned;
                    }
                }
                boolean changed = !order.equals(ids);
                return resequence(tripId, order)
                        .then(changed ? notifier.updated(trip, actorId, List.of("ordre de passage des arrêts modifié")) : Mono.<Void>empty())
                        .then(stopsOf(tripId));
            });
        });
    }

    // ── Manifeste (avant le départ) ──────────────────────────────────────────

    /** Ajoute une ligne de chargement à un arrêt d'un trajet planifié. */
    @Transactional
    public Mono<Void> addLine(UUID tripId, UUID stopId, UUID actorId, TripDetailInput input) {
        validateLine(input);
        return plannedTrip(tripId, actorId).flatMap(trip -> owned(tripId, stopId).flatMap(stop ->
                details.findAllByTripIdOrderBySortOrder(tripId).collectList().flatMap(all -> {
                    int next = all.stream().mapToInt(TripDetailEntity::getSortOrder).max().orElse(-1) + 1;
                    TripDetailEntity line = newLine(tripId, stopId, input, next);
                    return details.save(line).then(notifier.updated(trip, actorId,
                            List.of("manifeste : " + lineLabel(line) + " ajouté à l'arrêt « " + stop.getLocation() + " »")));
                })));
    }

    /** Modifie une ligne du manifeste (type, description, quantité, poids, arrêt) avant le départ. */
    @Transactional
    public Mono<Void> updateLine(UUID tripId, UUID detailId, UUID stopId, UUID actorId, TripDetailInput input) {
        validateLine(input);
        return plannedTrip(tripId, actorId).flatMap(trip -> ownedLine(tripId, detailId).flatMap(line -> {
            Mono<UUID> target = stopId == null ? Mono.just(line.getStopId()) : owned(tripId, stopId).map(TripStopEntity::getId);
            return target.flatMap(sid -> {
                line.setStopId(sid);
                line.setItemType(input.itemType());
                line.setDescription(input.description());
                line.setQuantity(input.quantity());
                line.setWeight(input.weight());
                line.setDepartureQuantity(input.departureQuantity() != null ? input.departureQuantity() : input.quantity());
                line.setNew(false);
                return details.save(line).then(notifier.updated(trip, actorId,
                        List.of("manifeste : " + lineLabel(line) + " modifié")));
            });
        }));
    }

    /** Retire une ligne du manifeste avant le départ. */
    @Transactional
    public Mono<Void> deleteLine(UUID tripId, UUID detailId, UUID actorId) {
        return plannedTrip(tripId, actorId).flatMap(trip -> ownedLine(tripId, detailId).flatMap(line ->
                details.delete(line).then(notifier.updated(trip, actorId,
                        List.of("manifeste : " + lineLabel(line) + " retiré")))));
    }

    private Mono<TripEntity> plannedTrip(UUID tripId, UUID actorId) {
        return tripFor(tripId, actorId, true).flatMap(trip -> "SCHEDULED".equals(trip.getStatus())
                ? Mono.just(trip)
                : Mono.error(TripException.manifestOnlyPlanned()));
    }

    private Mono<TripDetailEntity> ownedLine(UUID tripId, UUID detailId) {
        return details.findById(detailId)
                .filter(d -> tripId.equals(d.getTripId()))
                .switchIfEmpty(Mono.error(TripException.detailNotInTrip()));
    }

    static void validateLine(TripDetailInput input) {
        if (input == null || input.itemType() == null) {
            throw TripException.invalidManifestLine("Indiquez le type de chargement.");
        }
        try {
            com.yowyob.fleet.domain.model.TripDetail.ItemType.valueOf(input.itemType());
        } catch (IllegalArgumentException e) {
            throw TripException.invalidManifestLine("Type de chargement inconnu : " + input.itemType() + ".");
        }
        if (input.quantity() < 1) {
            throw TripException.invalidManifestLine("La quantité doit être d'au moins 1.");
        }
        if (input.departureQuantity() != null && input.departureQuantity() < 0) {
            throw TripException.invalidManifestLine("La quantité chargée ne peut pas être négative.");
        }
        if (input.weight() != null && input.weight().signum() < 0) {
            throw TripException.invalidManifestLine("Le poids ne peut pas être négatif.");
        }
        if (input.description() != null && input.description().length() > 255) {
            throw TripException.invalidManifestLine("La description ne doit pas dépasser 255 caractères.");
        }
    }

    private static String lineLabel(TripDetailEntity line) {
        String name = line.getDescription() != null && !line.getDescription().isBlank()
                ? "« " + line.getDescription().trim() + " »" : "ligne " + line.getItemType();
        return name + " × " + line.getQuantity();
    }

    // ── Exécution ────────────────────────────────────────────────────────────

    /** Le chauffeur est sur place. */
    @Transactional
    public Mono<TripStop> arrive(UUID tripId, UUID stopId, UUID actorId, LocalDateTime declaredAt) {
        return servable(tripId, stopId, actorId).flatMap(ctx -> {
            TripStopEntity stop = ctx.stop();
            if (!"PENDING".equals(stop.getStatus())) {
                return Mono.error(TripException.stopNotServable(stop.getStatus()));
            }
            stop.setStatus("ARRIVED");
            stop.setArrivedAt(TripStopRules.boundedTime(declaredAt, departedAt(ctx.trip()), now()));
            stop.setUpdatedAt(LocalDateTime.now());
            stop.setNew(false);
            return stops.save(stop).flatMap(saved -> {
                // Seule une arrivée en retard (au-delà de la tolérance) remonte au gestionnaire.
                Long delay = TripStopRules.delayMinutes(saved.getPlannedArrival(), saved.getArrivedAt());
                Mono<Void> late = "LATE".equals(TripStopRules.punctuality(saved.getPlannedArrival(), saved.getArrivedAt()))
                        ? notifier.lateArrival(ctx.trip(), saved, delay, actorId)
                        : Mono.empty();
                return late.thenReturn(toDomain(saved));
            });
        });
    }

    /**
     * Arrêt servi : quantités livrées ou ramassées par ligne (à défaut, la quantité prévue),
     * réceptionnaire obligatoire pour une livraison, preuve facultative déposée dans FleetMan.
     */
    @Transactional
    public Mono<TripStop> complete(UUID tripId, UUID stopId, UUID actorId, CompleteCommand cmd) {
        String proof = TripStopRules.clean(cmd.proofUrl());
        if (proof != null && (!proof.startsWith(FILE_PREFIX) || proof.length() > 500)) {
            return Mono.error(TripException.invalidProof());
        }
        return servable(tripId, stopId, actorId).flatMap(ctx -> {
            TripStopEntity stop = ctx.stop();
            String recipient = TripStopRules.clean(cmd.recipientName());
            if ("DELIVERY".equals(stop.getStopType()) && recipient == null) {
                return Mono.error(TripException.recipientRequired());
            }
            if (recipient != null && recipient.length() > 120) {
                return Mono.error(TripException.invalidStop("Le nom du réceptionnaire ne doit pas dépasser 120 caractères."));
            }
            Map<UUID, HandledLine> declared = new java.util.HashMap<>();
            for (HandledLine line : cmd.lines() != null ? cmd.lines() : List.<HandledLine>of()) {
                declared.put(line.detailId(), line);
            }
            return details.findAllByTripIdOrderBySortOrder(tripId).collectList().flatMap(all -> {
                List<TripDetailEntity> ofStop = all.stream().filter(d -> stopId.equals(d.getStopId())).toList();
                for (UUID id : declared.keySet()) {
                    if (ofStop.stream().noneMatch(d -> d.getId().equals(id))) {
                        return Mono.<TripStop>error(TripException.detailNotInTrip());
                    }
                }
                for (TripDetailEntity line : ofStop) {
                    int expected = expectedQuantity(line);
                    HandledLine handled = declared.get(line.getId());
                    int qty = handled != null && handled.quantity() != null ? handled.quantity() : expected;
                    String reason = handled != null ? handled.shortageReason() : null;
                    TripStopRules.validateHandled(stop.getStopType(), expected, qty, reason);
                    line.setDeliveredQuantity(qty);
                    line.setShortageReason(qty < expected ? reason : null);
                    line.setNew(false);
                }
                LocalDateTime lower = departedAt(ctx.trip());
                LocalDateTime arrivedAt = stop.getArrivedAt() != null ? stop.getArrivedAt()
                        : TripStopRules.boundedTime(cmd.arrivedAt(), lower, now());
                stop.setArrivedAt(arrivedAt);
                stop.setCompletedAt(TripStopRules.boundedTime(cmd.completedAt(), arrivedAt, now()));
                stop.setStatus("COMPLETED");
                stop.setRecipientName(recipient);
                stop.setProofUrl(proof);
                stop.setNotes(TripStopRules.clean(cmd.notes()));
                stop.setFailureReason(null);
                stop.setFailureNotes(null);
                stop.setUpdatedAt(LocalDateTime.now());
                stop.setNew(false);
                int shortages = (int) ofStop.stream()
                        .filter(l -> l.getDeliveredQuantity() != null && l.getDeliveredQuantity() < expectedQuantity(l))
                        .count();
                return details.saveAll(ofStop).then(stops.save(stop))
                        .flatMap(saved -> (shortages > 0 ? notifier.stopShortage(ctx.trip(), saved, shortages, actorId) : Mono.<Void>empty())
                                .then(closeTourIfDone(ctx.trip(), actorId))
                                .thenReturn(toDomain(saved)));
            });
        });
    }

    /** Arrêt non servi (refus, absent, fermé…) : rien n'est livré, le gestionnaire est prévenu. */
    @Transactional
    public Mono<TripStop> fail(UUID tripId, UUID stopId, UUID actorId, String reason, String notes) {
        TripStopRules.validateFailureReason(reason);
        return servable(tripId, stopId, actorId).flatMap(ctx -> close(ctx, "FAILED", reason, TripStopRules.clean(notes), actorId,
                saved -> notifier.stopFailed(ctx.trip(), saved, actorId)));
    }

    /** Le gestionnaire retire un arrêt de la tournée en route (client annulé, réorganisation). */
    @Transactional
    public Mono<TripStop> skip(UUID tripId, UUID stopId, UUID actorId, String reason) {
        String why = TripStopRules.clean(reason);
        if (why == null) {
            return Mono.error(TripException.invalidStop("Indiquez pourquoi l'arrêt est passé."));
        }
        return tripFor(tripId, actorId, true)
                .then(servable(tripId, stopId, actorId))
                .flatMap(ctx -> close(ctx, "SKIPPED", null, why, actorId,
                        saved -> notifier.stopSkipped(ctx.trip(), saved, why, actorId)));
    }

    // ── Liens avec le cycle du trajet ────────────────────────────────────────

    /** Arrivée « globale » (ancien écran) : les arrêts encore ouverts sont considérés servis. */
    public Mono<Void> completeRemaining(UUID tripId, LocalDateTime at, String notes) {
        return stops.findAllByTripIdOrderBySequence(tripId)
                .filter(s -> TripStopRules.isServable(s.getStatus()))
                .concatMap(s -> {
                    if (s.getArrivedAt() == null) s.setArrivedAt(at);
                    s.setCompletedAt(at);
                    s.setStatus("COMPLETED");
                    if (notes != null && !notes.isBlank()) s.setNotes(notes);
                    s.setUpdatedAt(LocalDateTime.now());
                    s.setNew(false);
                    return stops.save(s);
                })
                .then();
    }

    /** Retour enregistré : les arrêts non servis sont marqués passés. */
    public Mono<Void> closeRemaining(UUID tripId) {
        return stops.findAllByTripIdOrderBySequence(tripId)
                .filter(s -> TripStopRules.isServable(s.getStatus()))
                .concatMap(s -> {
                    s.setStatus("SKIPPED");
                    s.setFailureNotes("Non desservi : retour à la base enregistré.");
                    s.setCompletedAt(now());
                    s.setUpdatedAt(LocalDateTime.now());
                    s.setNew(false);
                    return stops.save(s);
                })
                .then();
    }

    /** Arrêt qui reçoit une ligne ajoutée en route : celui demandé, sinon le prochain ouvert, sinon le dernier. */
    public Mono<UUID> stopForNewLine(UUID tripId, UUID requested) {
        return stops.findAllByTripIdOrderBySequence(tripId).collectList().map(all -> {
            if (requested != null && all.stream().anyMatch(s -> s.getId().equals(requested))) {
                return requested;
            }
            return all.stream().filter(s -> TripStopRules.isServable(s.getStatus())).findFirst()
                    .or(() -> all.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(all.get(all.size() - 1)))
                    .map(TripStopEntity::getId)
                    .orElse(null);
        });
    }

    /** Une modification de destination sur un trajet à arrêt unique déplace cet arrêt. */
    public Mono<Void> moveSingleStop(UUID tripId, String location, BigDecimal lat, BigDecimal lng) {
        return stops.findAllByTripIdOrderBySequence(tripId).collectList().flatMap(all -> {
            if (all.size() != 1 || !"PENDING".equals(all.get(0).getStatus())) {
                return Mono.empty();
            }
            TripStopEntity stop = all.get(0);
            stop.setLocation(location.trim());
            stop.setLatitude(lat);
            stop.setLongitude(lng);
            stop.setUpdatedAt(LocalDateTime.now());
            stop.setNew(false);
            return stops.save(stop).then(syncDestination(tripId));
        });
    }

    // ── Interne ──────────────────────────────────────────────────────────────

    private record Context(TripEntity trip, TripStopEntity stop) {}

    private Mono<Context> servable(UUID tripId, UUID stopId, UUID actorId) {
        return tripFor(tripId, actorId, false).flatMap(trip -> {
            if (!"DEPARTED".equals(trip.getStatus())) {
                return Mono.error(TripException.stopActionNeedsDeparture());
            }
            return owned(tripId, stopId).flatMap(stop -> TripStopRules.isServable(stop.getStatus())
                    ? Mono.just(new Context(trip, stop))
                    : Mono.error(TripException.stopNotServable(stop.getStatus())));
        });
    }

    private Mono<TripStop> close(Context ctx, String status, String reason, String notes, UUID actorId,
                                 java.util.function.Function<TripStopEntity, Mono<Void>> notify) {
        TripStopEntity stop = ctx.stop();
        return details.findAllByTripIdOrderBySortOrder(stop.getTripId())
                .filter(d -> stop.getId().equals(d.getStopId()))
                .concatMap(d -> {
                    d.setDeliveredQuantity(0);
                    d.setShortageReason(null);
                    d.setNew(false);
                    return details.save(d);
                })
                .then(Mono.defer(() -> {
                    LocalDateTime at = now();
                    stop.setStatus(status);
                    stop.setFailureReason(reason);
                    stop.setFailureNotes(notes);
                    stop.setCompletedAt(at);
                    stop.setUpdatedAt(LocalDateTime.now());
                    stop.setNew(false);
                    return stops.save(stop);
                }))
                .flatMap(saved -> notify.apply(saved)
                        .then(closeTourIfDone(ctx.trip(), actorId))
                        .thenReturn(toDomain(saved)));
    }

    /** Tous les arrêts clos : livraisons terminées, le chauffeur rentre à la base. */
    private Mono<Void> closeTourIfDone(TripEntity trip, UUID actorId) {
        return stops.findAllByTripIdOrderBySequence(trip.getId()).collectList().flatMap(all -> {
            if (!TripStopRules.allTerminal(all.stream().map(TripStopEntity::getStatus).toList())) {
                return Mono.<Void>empty();
            }
            TripStopEntity last = all.stream()
                    .filter(s -> s.getCompletedAt() != null)
                    .max(java.util.Comparator.comparing(TripStopEntity::getCompletedAt))
                    .orElse(all.get(all.size() - 1));
            LocalDateTime at = last.getCompletedAt() != null ? last.getCompletedAt() : now();
            long served = all.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
            return db.sql("""
                    UPDATE fleet.trips SET status = 'ARRIVED', arrived_at = :at, arrival_location = :location, updated_at = NOW()
                    WHERE id = :id AND status = 'DEPARTED'
                    """)
                    .bind("at", at).bind("location", last.getLocation()).bind("id", trip.getId())
                    .fetch().rowsUpdated()
                    // Une seule notification : seulement quand c'est cette action qui clôt la tournée.
                    .flatMap(updated -> updated > 0 ? notifier.tourDone(trip, served, all.size(), actorId) : Mono.<Void>empty());
        });
    }

    /** Le trajet, si l'acteur en est le chauffeur ou gère sa flotte ({@code managerOnly} : gestionnaire seul). */
    private Mono<TripEntity> tripFor(UUID tripId, UUID actorId, boolean managerOnly) {
        return trips.findById(tripId)
                .switchIfEmpty(Mono.error(TripException.notFound(tripId)))
                .flatMap(trip -> accessScope.resolveLocalUserId(actorId).flatMap(localId ->
                        !managerOnly && (localId.equals(trip.getDriverId()) || actorId.equals(trip.getDriverId()))
                                ? Mono.just(trip)
                                : accessScope.assertTripFleetAccess(actorId, trip.getFleetId()).thenReturn(trip)));
    }

    private Mono<TripStopEntity> owned(UUID tripId, UUID stopId) {
        return stops.findByIdAndTripId(stopId, tripId).switchIfEmpty(Mono.error(TripException.stopNotFound()));
    }

    /** Numérotation 1…n dans l'ordre donné (contrainte d'unicité différée en fin de transaction). */
    private Mono<Void> resequence(UUID tripId, List<UUID> order) {
        return Flux.range(0, order.size())
                .concatMap(i -> db.sql("UPDATE fleet.trip_stops SET sequence = :seq, updated_at = NOW() WHERE id = :id AND trip_id = :trip")
                        .bind("seq", i + 1).bind("id", order.get(i)).bind("trip", tripId)
                        .fetch().rowsUpdated())
                .then(syncDestination(tripId));
    }

    /** La destination affichée du trajet est son dernier arrêt. */
    private Mono<Void> syncDestination(UUID tripId) {
        return db.sql("""
                UPDATE fleet.trips t SET destination_location = s.location, destination_lat = s.latitude,
                       destination_lng = s.longitude
                FROM (SELECT location, latitude, longitude FROM fleet.trip_stops
                      WHERE trip_id = :trip ORDER BY sequence DESC LIMIT 1) s
                WHERE t.id = :trip
                """)
                .bind("trip", tripId).fetch().rowsUpdated().then();
    }

    private static boolean isOpen(String status) {
        return "SCHEDULED".equals(status) || "DEPARTED".equals(status) || "ARRIVED".equals(status);
    }

    private static int expectedQuantity(TripDetailEntity line) {
        return line.getDepartureQuantity() != null ? Math.min(line.getDepartureQuantity(), line.getQuantity()) : line.getQuantity();
    }

    private static LocalDateTime departedAt(TripEntity trip) {
        return trip.getStartDate() != null ? ResourceAvailabilityService.slotStart(trip.getStartDate(), trip.getStartTime()) : null;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().withNano(0);
    }

    private static TripStopEntity newStop(TripEntity trip, TripStopRules.StopInput input, int sequence) {
        TripStopEntity stop = new TripStopEntity();
        stop.setId(UUID.randomUUID());
        stop.setTripId(trip.getId());
        stop.setSequence(sequence);
        stop.setStatus("PENDING");
        stop.setCreatedAt(LocalDateTime.now());
        apply(stop, input);
        stop.setNew(true);
        return stop;
    }

    private static void apply(TripStopEntity stop, TripStopRules.StopInput input) {
        stop.setStopType(input.stopType());
        stop.setLocation(input.location().trim());
        stop.setLatitude(input.latitude());
        stop.setLongitude(input.longitude());
        stop.setContactName(TripStopRules.clean(input.contactName()));
        stop.setContactPhone(TripStopRules.clean(input.contactPhone()));
        stop.setInstructions(TripStopRules.clean(input.instructions()));
        stop.setPlannedArrival(input.plannedArrival());
        stop.setUpdatedAt(LocalDateTime.now());
    }

    private static TripDetailEntity newLine(UUID tripId, UUID stopId, TripDetailInput input, int sortOrder) {
        TripDetailEntity line = new TripDetailEntity();
        line.setId(UUID.randomUUID());
        line.setTripId(tripId);
        line.setStopId(stopId);
        line.setItemType(input.itemType());
        line.setDescription(input.description());
        line.setQuantity(input.quantity());
        line.setWeight(input.weight());
        line.setDepartureQuantity(input.departureQuantity() != null ? input.departureQuantity() : input.quantity());
        line.setSortOrder(sortOrder);
        line.setNew(true);
        return line;
    }

    static TripStop toDomain(TripStopEntity s) {
        return new TripStop(s.getId(), s.getTripId(), s.getSequence(), s.getStopType(), s.getLocation(),
                s.getLatitude(), s.getLongitude(), s.getContactName(), s.getContactPhone(), s.getInstructions(),
                s.getPlannedArrival(), s.getStatus(), s.getArrivedAt(), s.getCompletedAt(), s.getRecipientName(),
                s.getProofUrl(), s.getNotes(), s.getFailureReason(), s.getFailureNotes(),
                TripStopRules.delayMinutes(s.getPlannedArrival(), s.getArrivedAt()),
                TripStopRules.punctuality(s.getPlannedArrival(), s.getArrivedAt()));
    }
}
