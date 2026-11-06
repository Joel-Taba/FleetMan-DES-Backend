package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import com.yowyob.fleet.domain.model.Incident;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Réponse représentant un incident terrain")
public record IncidentResponse(

        @Schema(description = "Identifiant unique de l'incident")
        UUID id,

        @Schema(description = "Type d'incident")
        String type,

        @Schema(description = "Description de l'incident")
        String description,

        @Schema(description = "Niveau de gravité")
        String severity,

        @Schema(description = "Statut courant dans le cycle de vie")
        String status,

        @Schema(description = "Date et heure de l'incident")
        LocalDateTime incidentDateTime,

        @Schema(description = "Date et heure de résolution")
        LocalDateTime resolvedAt,

        @Schema(description = "Coût de l'incident en FCFA")
        BigDecimal cost,

        @Schema(description = "Rapport de résolution")
        String report,

        @Schema(description = "Localisation GPS de l'incident")
        MaintenanceResponse.LocationDto location,

        @Schema(description = "Informations sur le témoin")
        WitnessDto witness,

        @Schema(description = "Numéro de procès-verbal de police")
        String policeReportNumber,

        @Schema(description = "Numéro de déclaration d'assurance")
        String insuranceClaimNumber,

        @Schema(description = "Personne ayant signalé l'incident")
        String reportedBy,

        @Schema(description = "Indique si l'incident est critique (HIGH ou CRITICAL)")
        boolean isCritical,

        @Schema(description = "Indique si l'incident est encore ouvert")
        boolean isOpen,

        @Schema(description = "ID du véhicule impliqué")
        UUID vehicleId,

        @Schema(description = "Numéro d'immatriculation du véhicule")
        String vehicleRegistration,

        @Schema(description = "ID du chauffeur impliqué")
        UUID driverId,

        @Schema(description = "Nom complet du chauffeur")
        String driverFullName,

        @Schema(description = "Le véhicule peut encore rouler (faux : déclaré non roulant)")
        boolean vehicleDrivable,

        @Schema(description = "Le véhicule est immobilisé par cet incident")
        boolean vehicleImmobilized,

        @Schema(description = "Lieu en clair (listes)")
        String locationLabel,

        @Schema(description = "Nombre de photos jointes (listes)")
        Integer photoCount,

        @Schema(description = "Nombre de pièces jointes (listes)")
        Integer attachmentCount,

        @Schema(description = "Nom du déclarant (listes)")
        String reportedByName,

        @Schema(description = "Responsable du dossier (listes)")
        String handlerName,

        @Schema(description = "Trajet en cours au moment de l'incident (listes)")
        UUID tripId,

        @Schema(description = "Code du trajet (listes)")
        String tripCode,

        @Schema(description = "Le trajet est interrompu par cet incident (listes)")
        Boolean tripInterrupted,

        @Schema(description = "Date d'enregistrement de la déclaration (listes)")
        LocalDateTime declaredAt,

        @Schema(description = "Dernière étape du dossier : date (listes)")
        LocalDateTime lastEventAt,

        @Schema(description = "Dernière étape du dossier : type (listes)")
        String lastEventType,

        @Schema(description = "Une demande d'information attend la réponse du chauffeur (listes)")
        Boolean infoRequestPending,

        @Schema(description = "Flotte de l'opération, figée à sa création (le véhicule peut avoir changé de flotte depuis)")
        java.util.UUID fleetId
) {
    /** Même réponse avec la flotte de l'opération. */
    public IncidentResponse withFleet(java.util.UUID fleet) {
        return new IncidentResponse(id, type, description, severity, status, incidentDateTime, resolvedAt, cost, report, location, witness, policeReportNumber, insuranceClaimNumber, reportedBy, isCritical, isOpen, vehicleId, vehicleRegistration, driverId, driverFullName, vehicleDrivable, vehicleImmobilized, locationLabel, photoCount, attachmentCount, reportedByName, handlerName, tripId, tripCode, tripInterrupted, declaredAt, lastEventAt, lastEventType, infoRequestPending, fleet);
    }


    /** Complète la réponse avec le résumé du dossier (listes). */
    public IncidentResponse withSummary(com.yowyob.fleet.application.service.IncidentCaseService.Summary s) {
        if (s == null) return this;
        return new IncidentResponse(id, type, description, severity, status, incidentDateTime, resolvedAt, cost, report, location,
                witness, policeReportNumber, insuranceClaimNumber, reportedBy, isCritical, isOpen, vehicleId, vehicleRegistration,
                driverId, driverFullName, vehicleDrivable, vehicleImmobilized, s.locationLabel(), s.photoCount(), s.attachmentCount(),
                s.reportedByName(), s.handlerName(), s.tripId(), s.tripCode(), s.tripInterrupted(), s.declaredAt(), s.lastEventAt(),
                s.lastEventType(), s.infoRequestPending(), fleetId);
    }

    /**
     * DTO imbriqué pour les informations du témoin.
     */
    public record WitnessDto(String name, String contact) {}

    /**
     * Méthode de fabrique : construit la réponse depuis le modèle domaine.
     */
    public static IncidentResponse from(Incident i) {
        MaintenanceResponse.LocationDto location = null;
        if (i.getLocation() != null) {
            location = new MaintenanceResponse.LocationDto(
                    i.getLocation().longitude(),
                    i.getLocation().latitude(),
                    null
            );
        }

        WitnessDto witness = null;
        if (i.getWitnessName() != null || i.getWitnessContact() != null) {
            witness = new WitnessDto(i.getWitnessName(), i.getWitnessContact());
        }

        return new IncidentResponse(
                i.getId(),
                i.getType() != null ? i.getType().name() : null,
                i.getDescription(),
                i.getSeverity() != null ? i.getSeverity().name() : null,
                i.getStatus() != null ? i.getStatus().name() : null,
                i.getIncidentDateTime(),
                i.getResolvedAt(),
                i.getCost(),
                i.getReport(),
                location,
                witness,
                i.getPoliceReportNumber(),
                i.getInsuranceClaimNumber(),
                i.getReportedBy(),
                i.isCritical(),
                i.isOpen(),
                i.getVehicleId(),
                i.getVehicleRegistration(),
                i.getDriverId(),
                i.getDriverFullName(),
                i.isVehicleDrivable(),
                i.isVehicleImmobilized(),
                null, null, null, null, null, null, null, null, null, null, null, null
        , null);
    }
}
