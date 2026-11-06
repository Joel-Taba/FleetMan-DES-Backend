package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.FuelRecharge;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse représentant une recharge de carburant")
public record FuelRechargeResponse(

        @Schema(description = "Identifiant unique de la recharge")
        UUID id,

        @Schema(description = "Quantité rechargée en litres")
        BigDecimal quantity,

        @Schema(description = "Prix total payé en FCFA")
        BigDecimal price,

        @Schema(description = "Coût unitaire calculé (prix / litre) en FCFA")
        BigDecimal unitCost,

        @Schema(description = "Date et heure de la recharge")
        LocalDateTime rechargeDateTime,

        @Schema(description = "Nom de la station-service")
        String stationName,

        @Schema(description = "Localisation GPS de la station")
        MaintenanceResponse.LocationDto location,

        @Schema(description = "ID du véhicule rechargé")
        UUID vehicleId,

        @Schema(description = "Numéro d'immatriculation du véhicule")
        String vehicleRegistration,

        @Schema(description = "ID du chauffeur")
        UUID driverId,

        @Schema(description = "Nom complet du chauffeur")
        String driverFullName,
        @Schema(description = "Nom de la station tel que saisi")
        String stationLabel,
        @Schema(description = "Kilométrage relevé au plein")
        BigDecimal odometerKm,
        @Schema(description = "Photo du ticket")
        String receiptUrl,
        String receiptName,
        String receiptMime,
        @Schema(description = "Auteur de la saisie")
        UUID createdBy,
        @Schema(description = "Plein déclaré par le chauffeur lui-même")
        boolean declaredByDriver,
        @Schema(description = "Dépense générée (circuit de validation)")
        UUID expenseId,
        @Schema(description = "Statut de la dépense : PENDING, APPROVED, REJECTED")
        String expenseStatus,
        @Schema(description = "Motif du rejet de la dépense")
        String expenseRejectionReason,
        @Schema(description = "Niveau de validation exigé : MANAGER ou ADMIN")
        String expenseApprovalLevel,

        @Schema(description = "Flotte de l'opération, figée à sa création (le véhicule peut avoir changé de flotte depuis)")
        java.util.UUID fleetId
) {
    /** Même réponse avec la flotte de l'opération. */
    public FuelRechargeResponse withFleet(java.util.UUID fleet) {
        return new FuelRechargeResponse(id, quantity, price, unitCost, rechargeDateTime, stationName, location, vehicleId, vehicleRegistration, driverId, driverFullName, stationLabel, odometerKm, receiptUrl, receiptName, receiptMime, createdBy, declaredByDriver, expenseId, expenseStatus, expenseRejectionReason, expenseApprovalLevel, fleet);
    }

    public static FuelRechargeResponse from(FuelRecharge f) {
        return from(f, null);
    }


    /**
     * Méthode de fabrique : construit la réponse depuis le modèle domaine.
     * Calcule automatiquement le coût unitaire via la méthode métier de l'entité.
     */
    public static FuelRechargeResponse from(FuelRecharge f,
                                            com.yowyob.fleet.application.service.OperationLookup.ExpenseBrief expense) {
        MaintenanceResponse.LocationDto location = null;
        if (f.getLocation() != null) {
            location = new MaintenanceResponse.LocationDto(
                    f.getLocation().longitude(),
                    f.getLocation().latitude(),
                    null
            );
        }

        return new FuelRechargeResponse(
                f.getId(),
                f.getQuantity(),
                f.getPrice(),
                f.unitCost(),   // méthode métier de l'entité domaine
                f.getRechargeDateTime(),
                f.getStationName() != null ? f.getStationName().name() : null,
                location,
                f.getVehicleId(),
                f.getVehicleRegistration(),
                f.getDriverId(),
                f.getDriverFullName(),
                f.getStationLabel(),
                f.getOdometerKm(),
                f.getReceiptUrl(),
                f.getReceiptName(),
                f.getReceiptMime(),
                f.getCreatedBy(),
                f.getCreatedBy() != null && f.getCreatedBy().equals(f.getDriverId()),
                expense != null ? expense.id() : null,
                expense != null ? expense.status() : null,
                expense != null ? expense.rejectionReason() : null,
                expense != null ? expense.approvalLevel() : null
        , null);
    }
}
