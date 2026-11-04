package com.yowyob.fleet.domain.model;

import com.yowyob.fleet.domain.exception.CompanyException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Fiche de l'entreprise et de ses sites : valeurs admises et complétude du dossier. */
public final class CompanyRules {

    public static final Set<String> LEGAL_FORMS = Set.of("ETS", "SARL", "SARLU", "SA", "SAS", "GIE", "COOPERATIVE", "OTHER");
    public static final Set<String> SECTORS = Set.of(
            "FREIGHT", "PASSENGER", "LOGISTICS", "DISTRIBUTION", "CONSTRUCTION", "EXTRACTIVE", "AGRICULTURE", "SERVICES", "OTHER");
    public static final Set<String> SITE_TYPES = Set.of("HEAD_OFFICE", "DEPOT", "AGENCY", "WORKSHOP");

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()-]{6,20}$");
    private static final Pattern WEBSITE = Pattern.compile("^(https?://)?[\\w-]+(\\.[\\w-]+)+(/\\S*)?$", Pattern.CASE_INSENSITIVE);

    /** Données saisies sur la fiche ; les champs vides sont stockés à null. */
    public record Profile(
            String name, String legalName, String legalForm, String registrationNumber, String taxId,
            String sector, String address, String city, String country, String phone, String email,
            String website
    ) {}

    private CompanyRules() {
    }

    public static Profile normalize(Profile p) {
        Profile n = new Profile(clean(p.name()), clean(p.legalName()), clean(p.legalForm()),
                clean(p.registrationNumber()), clean(p.taxId()), clean(p.sector()), clean(p.address()), clean(p.city()),
                p.country() == null || p.country().isBlank() ? "CM" : p.country().trim().toUpperCase(),
                clean(p.phone()), clean(p.email()), clean(p.website()));
        if (n.name() == null || n.name().length() < 2 || n.name().length() > 150) {
            throw CompanyException.invalidField("Le nom de l'entreprise doit compter de 2 à 150 caractères.");
        }
        maxLength(n.legalName(), 200, "La raison sociale");
        maxLength(n.registrationNumber(), 50, "Le numéro RCCM");
        maxLength(n.taxId(), 50, "Le numéro NIU");
        maxLength(n.address(), 255, "L'adresse");
        maxLength(n.city(), 100, "La ville");
        if (n.legalForm() != null && !LEGAL_FORMS.contains(n.legalForm())) {
            throw CompanyException.invalidField("Forme juridique inconnue.");
        }
        if (n.sector() != null && !SECTORS.contains(n.sector())) {
            throw CompanyException.invalidField("Secteur d'activité inconnu.");
        }
        if (n.country().length() != 2) {
            throw CompanyException.invalidField("Pays invalide.");
        }
        if (n.email() != null && (n.email().length() > 150 || !EMAIL.matcher(n.email()).matches())) {
            throw CompanyException.invalidField("Adresse e-mail de l'entreprise invalide.");
        }
        if (n.phone() != null && !PHONE.matcher(n.phone()).matches()) {
            throw CompanyException.invalidField("Téléphone de l'entreprise invalide.");
        }
        if (n.website() != null && (n.website().length() > 200 || !WEBSITE.matcher(n.website()).matches())) {
            throw CompanyException.invalidField("Adresse du site web invalide.");
        }
        return n;
    }

    public static void validateSite(String name, String type, String city, String phone) {
        String cleanName = clean(name);
        if (cleanName == null || cleanName.length() < 2 || cleanName.length() > 120) {
            throw CompanyException.invalidField("Le nom du site doit compter de 2 à 120 caractères.");
        }
        if (type == null || !SITE_TYPES.contains(type)) {
            throw CompanyException.invalidField("Type de site inconnu.");
        }
        if (clean(city) == null) {
            throw CompanyException.invalidField("La ville du site est obligatoire.");
        }
        String cleanPhone = clean(phone);
        if (cleanPhone != null && !PHONE.matcher(cleanPhone).matches()) {
            throw CompanyException.invalidField("Téléphone du site invalide.");
        }
    }

    /** Éléments attendus d'un dossier complet, sous forme de codes affichés par l'interface. */
    public static List<String> missingFields(Profile p, boolean hasLogo, boolean hasHeadOffice) {
        List<String> missing = new ArrayList<>();
        if (p.legalName() == null) missing.add("LEGAL_NAME");
        if (p.legalForm() == null) missing.add("LEGAL_FORM");
        if (p.registrationNumber() == null) missing.add("REGISTRATION_NUMBER");
        if (p.taxId() == null) missing.add("TAX_ID");
        if (p.sector() == null) missing.add("SECTOR");
        if (p.address() == null) missing.add("ADDRESS");
        if (p.city() == null) missing.add("CITY");
        if (p.phone() == null) missing.add("PHONE");
        if (p.email() == null) missing.add("EMAIL");
        if (!hasLogo) missing.add("LOGO");
        if (!hasHeadOffice) missing.add("HEAD_OFFICE");
        return missing;
    }

    /** Taux de complétude (le nom, toujours présent, compte comme un élément rempli). */
    public static int completeness(List<String> missing) {
        int total = 12;
        return Math.round((total - missing.size()) * 100f / total);
    }

    private static void maxLength(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw CompanyException.invalidField(label + " ne doit pas dépasser " + max + " caractères.");
        }
    }

    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
