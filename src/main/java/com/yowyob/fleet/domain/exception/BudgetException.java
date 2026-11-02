package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;
import java.math.BigDecimal;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Exceptions métier du module Dépenses & Budget.
 * Codes BDG_001 à BDG_015.
 */
public class BudgetException extends DomainException {

    protected BudgetException(String message, HttpStatus status, String businessCode) {
        super(message, status, businessCode);
    }

    // ── Dépenses (Expense) ────────────────────────────────────────────────────

    public static BudgetException expenseNotFound(UUID id) {
        return new BudgetException(
                "Dépense introuvable (ID: " + id + ").",
                HttpStatus.NOT_FOUND, "BDG_001"
        );
    }

    public static BudgetException expenseAmountRequired() {
        return new BudgetException(
                "Le montant de la dépense doit être strictement positif.",
                HttpStatus.BAD_REQUEST, "BDG_002"
        );
    }

    public static BudgetException expenseTypeRequired() {
        return new BudgetException(
                "Le type de dépense est obligatoire.",
                HttpStatus.BAD_REQUEST, "BDG_003"
        );
    }

    public static BudgetException expenseNotEditable(UUID id) {
        return new BudgetException(
                "La dépense (ID: " + id + ") ne peut pas être modifiée : elle est auto-générée ou déjà validée.",
                HttpStatus.UNPROCESSABLE_ENTITY, "BDG_004"
        );
    }

    public static BudgetException expenseAlreadyValidated(UUID id) {
        return new BudgetException(
                "La dépense (ID: " + id + ") a déjà été approuvée ou rejetée.",
                HttpStatus.UNPROCESSABLE_ENTITY, "BDG_005"
        );
    }

    public static BudgetException rejectionReasonRequired() {
        return new BudgetException(
                "Le motif de rejet est obligatoire.",
                HttpStatus.BAD_REQUEST, "BDG_006"
        );
    }

    public static BudgetException expenseDeleteForbidden(UUID id) {
        return new BudgetException(
                "La dépense (ID: " + id + ") est auto-générée et ne peut pas être supprimée.",
                HttpStatus.UNPROCESSABLE_ENTITY, "BDG_007"
        );
    }

    // ── Budgets ───────────────────────────────────────────────────────────────

    public static BudgetException budgetNotFound(UUID id) {
        return new BudgetException(
                "Budget introuvable (ID: " + id + ").",
                HttpStatus.NOT_FOUND, "BDG_008"
        );
    }

    public static BudgetException budgetAmountRequired() {
        return new BudgetException(
                "Le montant du budget doit être strictement positif.",
                HttpStatus.BAD_REQUEST, "BDG_009"
        );
    }

    public static BudgetException budgetAlreadyExists(UUID entityId, LocalDate month) {
        return new BudgetException(
                "Un budget existe déjà pour l'entité (ID: " + entityId + ") sur le mois " + month.toString().substring(0, 7) + ".",
                HttpStatus.CONFLICT, "BDG_010"
        );
    }

    public static BudgetException invalidBudgetMonth() {
        return new BudgetException(
                "Le mois du budget est invalide. Utilisez le format YYYY-MM.",
                HttpStatus.BAD_REQUEST, "BDG_011"
        );
    }

    // ── Références croisées ───────────────────────────────────────────────────

    public static BudgetException vehicleNotFound(UUID vehicleId) {
        return new BudgetException(
                "Véhicule introuvable pour cette dépense (ID: " + vehicleId + ").",
                HttpStatus.NOT_FOUND, "BDG_012"
        );
    }

    public static BudgetException fleetNotFound(UUID fleetId) {
        return new BudgetException(
                "Flotte introuvable pour ce budget (ID: " + fleetId + ").",
                HttpStatus.NOT_FOUND, "BDG_013"
        );
    }

    public static BudgetException driverNotFound(UUID driverId) {
        return new BudgetException(
                "Chauffeur introuvable pour cette dépense (ID: " + driverId + ").",
                HttpStatus.NOT_FOUND, "BDG_014"
        );
    }

    public static BudgetException invalidDateRange() {
        return new BudgetException(
                "La date de début doit être antérieure à la date de fin.",
                HttpStatus.BAD_REQUEST, "BDG_015"
        );
    }

    // ── Circuit de validation et enveloppes (057) ────────────────────────────

    public static BudgetException receiptRequired() {
        return new BudgetException("Joignez le justificatif (photo du ticket, avis d'amende, facture) avant d'approuver cette dépense.",
                HttpStatus.UNPROCESSABLE_ENTITY, "BDG_016");
    }

    public static BudgetException selfApproval() {
        return new BudgetException("Vous ne pouvez pas valider une dépense que vous avez saisie vous-même.",
                HttpStatus.FORBIDDEN, "BDG_017");
    }

    public static BudgetException adminApprovalRequired(String reason) {
        return new BudgetException("Cette dépense doit être validée par l'administrateur de l'entreprise (" + reason + ").",
                HttpStatus.FORBIDDEN, "BDG_018");
    }

    public static BudgetException futureExpenseDate() {
        return new BudgetException("La date de la dépense ne peut pas être dans le futur.", HttpStatus.BAD_REQUEST, "BDG_019");
    }

    public static BudgetException driverNotInVehicleFleet() {
        return new BudgetException("Ce chauffeur n'appartient pas à la flotte du véhicule.", HttpStatus.BAD_REQUEST, "BDG_020");
    }

    public static BudgetException vehicleBudgetsExceedEnvelope(BigDecimal allocated, BigDecimal envelope) {
        return new BudgetException("Les budgets des véhicules (" + allocated.stripTrailingZeros().toPlainString()
                + " XAF) dépasseraient l'enveloppe de la flotte (" + envelope.stripTrailingZeros().toPlainString() + " XAF).",
                HttpStatus.CONFLICT, "BDG_021");
    }

    public static BudgetException fleetEnvelopeRequired() {
        return new BudgetException("La flotte n'a pas d'enveloppe pour ce mois : l'administrateur doit d'abord la fixer.",
                HttpStatus.UNPROCESSABLE_ENTITY, "BDG_022");
    }

    public static BudgetException monthClosed() {
        return new BudgetException("Un mois écoulé est clos : ses budgets ne se créent ni ne se modifient plus.",
                HttpStatus.UNPROCESSABLE_ENTITY, "BDG_023");
    }

    public static BudgetException fleetEnvelopeAdminOnly() {
        return new BudgetException("L'enveloppe d'une flotte est fixée par l'administrateur de l'entreprise.",
                HttpStatus.FORBIDDEN, "BDG_024");
    }

    public static BudgetException envelopeHasVehicleBudgets() {
        return new BudgetException("Cette enveloppe est répartie en budgets de véhicules : supprimez-les d'abord.",
                HttpStatus.CONFLICT, "BDG_025");
    }

    public static BudgetException descriptionRequired() {
        return new BudgetException("Décrivez la dépense (nature, lieu) pour une dépense « Autre ».", HttpStatus.BAD_REQUEST, "BDG_026");
    }

    public static BudgetException approvedNotDeletable() {
        return new BudgetException("Une dépense approuvée fait partie des comptes : elle ne peut plus être supprimée.",
                HttpStatus.UNPROCESSABLE_ENTITY, "BDG_027");
    }

    public static BudgetException automaticTypeNotManual() {
        return new BudgetException("Carburant, maintenance et incidents sont comptés depuis leur opération : "
                + "enregistrez le plein, la maintenance ou l'incident (une saisie manuelle les compterait deux fois).",
                HttpStatus.BAD_REQUEST, "BDG_028");
    }
}

