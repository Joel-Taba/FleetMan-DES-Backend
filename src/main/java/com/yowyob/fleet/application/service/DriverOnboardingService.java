package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.DriverException;
import com.yowyob.fleet.domain.model.DocumentCatalog;
import com.yowyob.fleet.domain.model.DocumentCatalog.TargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Finalisation de l'inscription par le chauffeur, après activation de son compte : il complète
 * son profil et dépose toutes les pièces obligatoires de son entreprise (les facultatives
 * peuvent suivre) avant d'accéder à son interface de travail. Une pièce déposée en attente
 * de contrôle suffit pour finir l'inscription ; seule une pièce approuvée permet d'être engagé.
 */
@Service
@RequiredArgsConstructor
public class DriverOnboardingService {

    /** Une pièce obligatoire est « fournie » si elle est en attente de contrôle ou en vigueur. */
    static final Set<String> PROVIDED_STATES = Set.of("PENDING", "OK", "EXPIRING");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{8,15}$");

    public record Profile(String firstName, String lastName, String phone, String email, String licenceNumber,
                          String photoUrl, LocalDate birthDate, String address, String emergencyContactName,
                          String emergencyContactPhone, String fleetName) {}

    public record DocItem(String docType, String label, String state) {}

    public record Onboarding(boolean completed, LocalDateTime completedAt, Profile profile, List<String> missingProfile,
                             List<DocItem> documents, List<String> missingDocuments, boolean canComplete) {}

    public record ProfileUpdate(LocalDate birthDate, String address, String emergencyContactName,
                                String emergencyContactPhone, String phone) {}

    private final DatabaseClient db;
    private final AccessScopeService accessScope;
    private final DocumentService documents;
    private final AppNotifier notifier;

    /** Champs de profil encore vides (codes stables, traduits par les applications). */
    static List<String> missingProfile(Profile p) {
        List<String> missing = new ArrayList<>();
        if (blank(p.photoUrl())) missing.add("PHOTO");
        if (blank(p.phone())) missing.add("PHONE");
        if (p.birthDate() == null) missing.add("BIRTH_DATE");
        if (blank(p.address())) missing.add("ADDRESS");
        if (blank(p.emergencyContactName())) missing.add("EMERGENCY_CONTACT_NAME");
        if (blank(p.emergencyContactPhone())) missing.add("EMERGENCY_CONTACT_PHONE");
        return missing;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static DriverException invalid(String message) {
        return new DriverException(message, HttpStatus.BAD_REQUEST, "ONB_001");
    }

    private static String phoneOrNull(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        String phone = raw.trim().replaceAll("[^0-9+]", "");
        if (!PHONE.matcher(phone).matches()) throw invalid(field + " : numéro invalide.");
        return phone;
    }

    private static String textOrNull(String raw, int max, String field) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().replaceAll("\\s+", " ");
        if (v.length() > max) throw invalid(field + " dépasse " + max + " caractères.");
        return v;
    }

    public Mono<Onboarding> status(UUID actorId) {
        return accessScope.resolveLocalUserId(actorId).flatMap(driverId -> Mono.zip(
                        profile(driverId),
                        documents.checklist(actorId, TargetType.DRIVER, driverId).filter(i -> i.mandatory()).collectList(),
                        completedAt(driverId))
                .map(t -> {
                    Profile p = t.getT1();
                    List<DocItem> docs = t.getT2().stream()
                            .map(i -> new DocItem(i.docType(), DocumentCatalog.label(TargetType.DRIVER, i.docType()), i.state()))
                            .toList();
                    List<String> missingDocs = docs.stream().filter(d -> !PROVIDED_STATES.contains(d.state()))
                            .map(DocItem::docType).toList();
                    List<String> missing = missingProfile(p);
                    LocalDateTime done = t.getT3().orElse(null);
                    return new Onboarding(done != null, done, p, missing, docs, missingDocs,
                            missing.isEmpty() && missingDocs.isEmpty());
                }));
    }

    public Mono<Onboarding> updateProfile(UUID actorId, ProfileUpdate req) {
        LocalDate birth = req.birthDate();
        String address;
        String contactName;
        String contactPhone;
        String phone;
        try {
            if (birth != null) {
                int age = Period.between(birth, LocalDate.now()).getYears();
                if (age < 18 || age > 80) throw invalid("Date de naissance : un chauffeur doit avoir entre 18 et 80 ans.");
            }
            address = textOrNull(req.address(), 255, "Adresse");
            contactName = textOrNull(req.emergencyContactName(), 120, "Contact d'urgence");
            contactPhone = phoneOrNull(req.emergencyContactPhone(), "Téléphone du contact d'urgence");
            phone = phoneOrNull(req.phone(), "Téléphone");
        } catch (DriverException e) {
            return Mono.error(e);
        }
        return accessScope.resolveLocalUserId(actorId).flatMap(driverId -> {
            var spec = db.sql("""
                            UPDATE fleet.drivers SET
                                birth_date = COALESCE(:birth, birth_date),
                                address = COALESCE(:address, address),
                                emergency_contact_name = COALESCE(:contactName, emergency_contact_name),
                                emergency_contact_phone = COALESCE(:contactPhone, emergency_contact_phone),
                                updated_at = :now
                            WHERE user_id = :id
                            """)
                    .bind("now", LocalDateTime.now()).bind("id", driverId);
            spec = birth != null ? spec.bind("birth", birth) : spec.bindNull("birth", LocalDate.class);
            spec = address != null ? spec.bind("address", address) : spec.bindNull("address", String.class);
            spec = contactName != null ? spec.bind("contactName", contactName) : spec.bindNull("contactName", String.class);
            spec = contactPhone != null ? spec.bind("contactPhone", contactPhone) : spec.bindNull("contactPhone", String.class);
            // Le téléphone du compte ne se renseigne ici que s'il manque (inscription par e-mail) ;
            // le modifier ensuite passe par « Mon profil ».
            Mono<Long> phoneUpdate = phone == null ? Mono.just(0L) : db.sql("""
                            UPDATE fleet.users SET phone = :phone WHERE id = :id AND (phone IS NULL OR phone = '')
                            """)
                    .bind("phone", phone).bind("id", driverId).fetch().rowsUpdated();
            return spec.fetch().rowsUpdated().then(phoneUpdate).then(status(actorId));
        });
    }

    /** Termine l'inscription si le profil est complet et chaque pièce obligatoire déposée. */
    public Mono<Onboarding> complete(UUID actorId) {
        return status(actorId).flatMap(o -> {
            if (o.completed()) return Mono.just(o);
            if (!o.canComplete()) {
                List<String> parts = new ArrayList<>();
                if (!o.missingProfile().isEmpty()) parts.add("profil incomplet");
                if (!o.missingDocuments().isEmpty()) {
                    parts.add("pièces à déposer : " + String.join(", ", o.documents().stream()
                            .filter(d -> o.missingDocuments().contains(d.docType())).map(DocItem::label).toList()));
                }
                return Mono.error(new DriverException("Inscription incomplète (" + String.join(" ; ", parts) + ").",
                        HttpStatus.CONFLICT, "ONB_002"));
            }
            return accessScope.resolveLocalUserId(actorId).flatMap(driverId -> db.sql("""
                                    UPDATE fleet.drivers SET onboarding_completed_at = :now
                                    WHERE user_id = :id AND onboarding_completed_at IS NULL
                                    """)
                            .bind("now", LocalDateTime.now()).bind("id", driverId)
                            .fetch().rowsUpdated()
                            .then(db.sql("SELECT fleet_id FROM fleet.drivers WHERE user_id = :id").bind("id", driverId)
                                    .map((row, meta) -> row.get("fleet_id", UUID.class)).one())
                            .flatMap(fleetId -> notifier.toFleetManager(fleetId, driverId, "Inscription terminée",
                                    o.profile().firstName() + " " + o.profile().lastName()
                                            + " a complété son profil et déposé ses pièces obligatoires. Contrôlez son dossier.",
                                    AppNotifier.Level.INFO, "DRIVER_ONBOARDING_COMPLETED",
                                    AppNotifier.refs("driverId", driverId))))
                    .then(status(actorId));
        });
    }

    /**
     * Passe la finalisation (profil ou pièces incomplets) : l'interface de travail s'ouvre, mais
     * le chauffeur reste non engageable tant que ses pièces obligatoires ne sont pas approuvées.
     * Le gestionnaire est prévenu ; le chauffeur complète ensuite depuis « Mon profil » et « Mes documents ».
     */
    public Mono<Onboarding> skip(UUID actorId) {
        return status(actorId).flatMap(o -> {
            if (o.completed()) return Mono.just(o);
            return accessScope.resolveLocalUserId(actorId).flatMap(driverId -> db.sql("""
                                    UPDATE fleet.drivers SET onboarding_completed_at = :now
                                    WHERE user_id = :id AND onboarding_completed_at IS NULL
                                    """)
                            .bind("now", LocalDateTime.now()).bind("id", driverId)
                            .fetch().rowsUpdated()
                            .then(db.sql("SELECT fleet_id FROM fleet.drivers WHERE user_id = :id").bind("id", driverId)
                                    .map((row, meta) -> row.get("fleet_id", UUID.class)).one())
                            .flatMap(fleetId -> notifier.toFleetManager(fleetId, driverId, "Inscription à compléter",
                                    o.profile().firstName() + " " + o.profile().lastName()
                                            + " a accédé à son espace sans terminer son inscription (profil ou pièces obligatoires manquants). "
                                            + "Il ne peut pas être engagé tant que son dossier n'est pas en règle.",
                                    AppNotifier.Level.WARNING, "DRIVER_ONBOARDING_SKIPPED",
                                    AppNotifier.refs("driverId", driverId))))
                    .then(status(actorId));
        });
    }

    private Mono<Profile> profile(UUID driverId) {
        return db.sql("""
                        SELECT u.first_name, u.last_name, u.phone, u.email, u.photo_url, d.licence_number, d.birth_date,
                               d.address, d.emergency_contact_name, d.emergency_contact_phone, f.name AS fleet_name
                        FROM fleet.drivers d
                        JOIN fleet.users u ON u.id = d.user_id
                        LEFT JOIN fleet.fleets f ON f.id = d.fleet_id
                        WHERE d.user_id = :id
                        """)
                .bind("id", driverId)
                .map((row, meta) -> {
                    String email = row.get("email", String.class);
                    return new Profile(row.get("first_name", String.class), row.get("last_name", String.class),
                            row.get("phone", String.class),
                            // Adresse technique créée faute d'e-mail : non affichée.
                            email != null && email.endsWith("@fleetman.local") ? null : email,
                            row.get("licence_number", String.class), row.get("photo_url", String.class),
                            row.get("birth_date", LocalDate.class), row.get("address", String.class),
                            row.get("emergency_contact_name", String.class), row.get("emergency_contact_phone", String.class),
                            row.get("fleet_name", String.class));
                })
                .one()
                .switchIfEmpty(Mono.error(DriverException.notFound()));
    }

    private Mono<java.util.Optional<LocalDateTime>> completedAt(UUID driverId) {
        return db.sql("SELECT onboarding_completed_at FROM fleet.drivers WHERE user_id = :id")
                .bind("id", driverId)
                .map((row, meta) -> java.util.Optional.ofNullable(row.get("onboarding_completed_at", LocalDateTime.class)))
                .one()
                .defaultIfEmpty(java.util.Optional.empty());
    }
}
