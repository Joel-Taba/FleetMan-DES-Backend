package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

public class CompanyException extends DomainException {

    private CompanyException(String message, HttpStatus status, String code) {
        super(message, status, code);
    }

    public static CompanyException invalidField(String message) {
        return new CompanyException(message, HttpStatus.BAD_REQUEST, "ORG_001");
    }

    public static CompanyException siteNotFound() {
        return new CompanyException("Site introuvable dans votre entreprise.", HttpStatus.NOT_FOUND, "ORG_002");
    }

    public static CompanyException siteNameTaken(String name) {
        return new CompanyException("Un site porte déjà le nom « " + name + " ».", HttpStatus.CONFLICT, "ORG_003");
    }

    public static CompanyException headOfficeExists() {
        return new CompanyException("L'entreprise a déjà un siège : modifiez-le ou changez son type.",
                HttpStatus.CONFLICT, "ORG_004");
    }

    public static CompanyException siteInUse(long fleets) {
        return new CompanyException("Ce site accueille encore " + fleets + " flotte(s) : rattachez-les d'abord à un autre site.",
                HttpStatus.CONFLICT, "ORG_005");
    }

    public static CompanyException siteInactive() {
        return new CompanyException("Ce site est désactivé : une flotte ne peut pas y être rattachée.",
                HttpStatus.CONFLICT, "ORG_006");
    }

    public static CompanyException fleetNotFound() {
        return new CompanyException("Flotte introuvable dans votre entreprise.", HttpStatus.NOT_FOUND, "ORG_007");
    }
}
