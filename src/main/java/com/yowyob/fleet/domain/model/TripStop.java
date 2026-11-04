package com.yowyob.fleet.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Arrêt d'une tournée : point de livraison ou de ramassage, avec son exécution. */
public record TripStop(
        UUID id,
        UUID tripId,
        int sequence,
        String stopType,
        String location,
        BigDecimal latitude,
        BigDecimal longitude,
        String contactName,
        String contactPhone,
        String instructions,
        LocalDateTime plannedArrival,
        String status,
        LocalDateTime arrivedAt,
        LocalDateTime completedAt,
        String recipientName,
        String proofUrl,
        String notes,
        String failureReason,
        String failureNotes,
        Long delayMinutes,
        String punctuality
) {}
