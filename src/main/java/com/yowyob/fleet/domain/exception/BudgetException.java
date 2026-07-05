package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

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
}
