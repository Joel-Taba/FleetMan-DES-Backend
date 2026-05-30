package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

public class ManagerException extends DomainException {

    public ManagerException(String message, HttpStatus status, String code) {
        super(message, status, code);
    }

    public static ManagerException invalidCompanyData(String details) {
        return new ManagerException("Le nom de l'entreprise est invalide : " + details, HttpStatus.BAD_REQUEST, "MGR_001");
    }

    public static ManagerException profileInconsistency() {
        return new ManagerException("Incohérence entre l'identité connectée et la ressource demandée.", HttpStatus.BAD_REQUEST, "MGR_002");
    }

    public static ManagerException accessDenied() {
        return new ManagerException("Accès réservé aux gestionnaires de flotte (Rôle FLEET_MANAGER requis).", HttpStatus.FORBIDDEN, "MGR_003");
    }

    public static ManagerException profileNotFound() {
        return new ManagerException("Profil gestionnaire introuvable. Veuillez vous reconnecter pour forcer la synchronisation.", HttpStatus.NOT_FOUND, "MGR_004");
    }

    public static ManagerException kpiCalculationFailed() {
        return new ManagerException("Impossible de calculer les statistiques (KPIs) pour le moment.", HttpStatus.INTERNAL_SERVER_ERROR, "MGR_005");
    }
}