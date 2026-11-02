package com.yowyob.fleet.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * Pièces connues par type de cible. Le socle légal (assurance, visite technique, carte grise,
 * permis) est toujours obligatoire ; l'entreprise peut en rendre d'autres obligatoires
 * (fleet.document_policies). Même catalogue côté front : lib/documents.ts.
 */
public final class DocumentCatalog {

    public enum TargetType { VEHICLE, DRIVER, MANAGER }

    public static final String OTHER = "OTHER";
    public static final String DRIVING_LICENSE = "DRIVING_LICENSE";

    public record Entry(TargetType target, String docType, String label, boolean legal, boolean expiryRequired) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry(TargetType.VEHICLE, "INSURANCE", "assurance", true, true),
            new Entry(TargetType.VEHICLE, "TECHNICAL_CONTROL", "visite technique", true, true),
            new Entry(TargetType.VEHICLE, "REGISTRATION", "carte grise", true, false),
            new Entry(TargetType.VEHICLE, "TAX_STICKER", "vignette", false, true),
            new Entry(TargetType.VEHICLE, "TRANSPORT_PERMIT", "licence de transport", false, true),
            new Entry(TargetType.VEHICLE, OTHER, "autre document", false, false),
            new Entry(TargetType.DRIVER, DRIVING_LICENSE, "permis de conduire", true, true),
            new Entry(TargetType.DRIVER, "MEDICAL_CERT", "visite médicale", false, true),
            new Entry(TargetType.DRIVER, "PROFESSIONAL_CARD", "carte professionnelle", false, true),
            new Entry(TargetType.DRIVER, "WORK_CONTRACT", "contrat de travail", false, false),
            new Entry(TargetType.DRIVER, "ID_CARD", "pièce d'identité", false, false),
            new Entry(TargetType.DRIVER, OTHER, "autre document", false, false),
            new Entry(TargetType.MANAGER, "ID_CARD", "pièce d'identité", false, false),
            new Entry(TargetType.MANAGER, "WORK_CONTRACT", "contrat de travail", false, false),
            new Entry(TargetType.MANAGER, OTHER, "autre document", false, false));

    private DocumentCatalog() {
    }

    public static List<Entry> entries(TargetType target) {
        return ENTRIES.stream().filter(e -> e.target() == target).toList();
    }

    public static Optional<Entry> find(TargetType target, String docType) {
        return ENTRIES.stream().filter(e -> e.target() == target && e.docType().equals(docType)).findFirst();
    }

    public static List<String> legalTypes(TargetType target) {
        return entries(target).stream().filter(Entry::legal).map(Entry::docType).toList();
    }

    public static String label(TargetType target, String docType) {
        return find(target, docType).map(Entry::label).orElse(docType);
    }
}
