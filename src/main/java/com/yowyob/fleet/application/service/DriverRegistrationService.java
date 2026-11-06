package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.DriverException;
import com.yowyob.fleet.domain.ports.in.AuthUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.domain.ports.out.DriverPersistencePort;
import com.yowyob.fleet.domain.ports.out.MailPort;
import com.yowyob.fleet.domain.ports.out.SmsPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DriverRegistrationRequest;
import io.r2dbc.spi.Readable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Inscription des chauffeurs en deux phases, sans intervention de l'administrateur.
 * <ol>
 *   <li>Le chauffeur demande à rejoindre une flotte avec le code d'inscription de la flotte,
 *       ses informations d'identification et un téléphone ou un e-mail.</li>
 *   <li>Le gestionnaire de la flotte est notifié ; il accepte (un code d'activation est généré,
 *       qu'il envoie ou remet en main propre) ou refuse avec un motif.</li>
 *   <li>Le chauffeur active son compte avec ce code et choisit son mot de passe : le compte
 *       n'existe qu'à ce moment, personne d'autre ne connaît le mot de passe.</li>
 * </ol>
 * Le code : 8 caractères sans ambiguïté (pas de 0/O, 1/I/L), valable 72 h, 5 essais, à usage unique.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverRegistrationService {

    public static final int CODE_VALIDITY_HOURS = 72;
    public static final int MAX_ATTEMPTS = 5;
    static final int MAX_PENDING_PER_FLEET = 30;
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{8,15}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final SecureRandom RANDOM = new SecureRandom();
    // Valeur de liaison qui ne correspond à aucune coordonnée (PostgreSQL refuse l'octet nul).
    private static final String NONE = "#none#";
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    public record RegistrationRequest(String fleetCode, String firstName, String lastName, String phone, String email,
                                      String licenceNumber) {}

    public record Submitted(UUID id, String fleetName, String status) {}

    public record ActivationRequest(String contact, String code, String password) {}

    public record Activated(String username, AuthPort.AuthResponse auth) {}

    public record Registration(UUID id, UUID fleetId, String fleetName, String firstName, String lastName, String phone,
                               String email, String licenceNumber, String status, String rejectionReason,
                               String activationCode, LocalDateTime codeExpiresAt, boolean codeExpired, boolean codeLocked,
                               LocalDateTime codeSentAt, String codeSentChannels, UUID driverUserId,
                               LocalDateTime activatedAt, Boolean onboardingCompleted, LocalDateTime createdAt,
                               LocalDateTime reviewedAt) {}

    public record Delivery(String channel, String destination, boolean delivered) {}

    public record SendResult(Registration registration, List<Delivery> deliveries) {}

    public record FleetCode(UUID fleetId, String fleetName, String code) {}

    private final DatabaseClient db;
    private final AccessScopeService accessScope;
    private final AppNotifier notifier;
    private final MailPort mail;
    private final SmsPort sms;
    private final DriverService drivers;
    private final DriverPersistencePort driverPersistence;
    private final PlanLimitGuard planLimitGuard;
    private final AuthUseCase authUseCase;

    // ── Règles pures (testées unitairement) ─────────────────────────────────

    static String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.trim().replaceAll("[^0-9+]", "");
        return digits.isEmpty() ? null : digits;
    }

    static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Code saisi : casse, espaces et tirets ignorés. */
    static String normalizeCode(String code) {
        return code == null ? "" : code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    static String formatCode(String code) {
        return code == null || code.length() != 8 ? code : code.substring(0, 4) + "-" + code.substring(4);
    }

    static String newCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return sb.toString();
    }

    /** Mot de passe choisi par le chauffeur, règle du Kernel : 10 caractères, majuscule, minuscule, chiffre, symbole. */
    static void checkPassword(String password) {
        boolean strong = password != null && password.length() >= 10
                && password.matches(".*[A-Z].*") && password.matches(".*[a-z].*")
                && password.matches(".*[0-9].*") && password.matches(".*[^A-Za-z0-9].*");
        if (!strong) {
            throw error("Le mot de passe doit contenir au moins 10 caractères, avec une majuscule, une minuscule, un chiffre et un symbole.",
                    HttpStatus.BAD_REQUEST, "REG_010");
        }
    }

    static String usernameBase(String first, String last) {
        String raw = (first + "." + last).toLowerCase(Locale.ROOT);
        String ascii = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String cleaned = ascii.replaceAll("\\s+", ".").replaceAll("[^a-z0-9.]", "").replaceAll("\\.+", ".");
        return cleaned.isBlank() || ".".equals(cleaned) ? "chauffeur" : cleaned;
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(at, 0));
        return email.charAt(0) + "***" + email.substring(at);
    }

    static String maskPhone(String phone) {
        return phone.length() <= 4 ? "***" : "***" + phone.substring(phone.length() - 3);
    }

    private static DriverException error(String message, HttpStatus status, String code) {
        return new DriverException(message, status, code);
    }

    private static String trimmed(String value, int max, String field) {
        String v = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (v.isEmpty()) throw error(field + " est obligatoire.", HttpStatus.BAD_REQUEST, "REG_001");
        if (v.length() > max) throw error(field + " dépasse " + max + " caractères.", HttpStatus.BAD_REQUEST, "REG_001");
        return v;
    }

    // ── Phase 1 : demande du chauffeur (sans session) ───────────────────────

    public Mono<Submitted> submit(RegistrationRequest req) {
        String first;
        String last;
        String licence;
        String phone = normalizePhone(req.phone());
        String email = normalizeEmail(req.email());
        try {
            first = trimmed(req.firstName(), 100, "Le prénom");
            last = trimmed(req.lastName(), 100, "Le nom");
            licence = trimmed(req.licenceNumber(), 50, "Le numéro de permis").toUpperCase(Locale.ROOT);
        } catch (DriverException e) {
            return Mono.error(e);
        }
        if (phone == null && email == null) {
            return Mono.error(error("Indiquez un numéro de téléphone ou une adresse e-mail pour recevoir votre code d'activation.",
                    HttpStatus.BAD_REQUEST, "REG_002"));
        }
        if (phone != null && !PHONE.matcher(phone).matches()) {
            return Mono.error(error("Numéro de téléphone invalide.", HttpStatus.BAD_REQUEST, "REG_003"));
        }
        if (email != null && !EMAIL.matcher(email).matches()) {
            return Mono.error(error("Adresse e-mail invalide.", HttpStatus.BAD_REQUEST, "REG_003"));
        }
        String fleetCode = normalizeCode(req.fleetCode());
        if (fleetCode.isEmpty()) {
            return Mono.error(error("Le code de la flotte est obligatoire : demandez-le à votre gestionnaire.",
                    HttpStatus.BAD_REQUEST, "REG_004"));
        }
        return db.sql("""
                        SELECT f.id, f.name FROM fleet.fleets f
                        JOIN fleet.users u ON u.id = f.manager_id
                        WHERE f.registration_code = :code AND f.deleted_at IS NULL AND COALESCE(u.is_active, TRUE)
                        """)
                .bind("code", fleetCode)
                .map((row, meta) -> new FleetCode(row.get("id", UUID.class), row.get("name", String.class), fleetCode))
                .one()
                .switchIfEmpty(Mono.error(error("Code de flotte inconnu. Vérifiez-le auprès de votre gestionnaire.",
                        HttpStatus.NOT_FOUND, "REG_005")))
                .flatMap(fleet -> assertNoDuplicate(phone, email, licence)
                        .then(assertFleetQueue(fleet.fleetId()))
                        .then(Mono.defer(() -> {
                            UUID id = UUID.randomUUID();
                            var spec = db.sql("""
                                            INSERT INTO fleet.driver_registrations
                                                (id, fleet_id, first_name, last_name, phone, email, licence_number, status, created_at, tenant_id)
                                            SELECT :id, f.id, :first, :last, :phone, :email, :licence, 'PENDING', :now, f.tenant_id
                                            FROM fleet.fleets f WHERE f.id = :fleet
                                            """)
                                    .bind("id", id).bind("fleet", fleet.fleetId()).bind("first", first).bind("last", last)
                                    .bind("licence", licence).bind("now", LocalDateTime.now());
                            spec = phone != null ? spec.bind("phone", phone) : spec.bindNull("phone", String.class);
                            spec = email != null ? spec.bind("email", email) : spec.bindNull("email", String.class);
                            return spec.fetch().rowsUpdated()
                                    .then(notifier.toFleetManager(fleet.fleetId(), null, "Nouvelle demande d'inscription",
                                            first + " " + last + " demande à rejoindre la flotte " + fleet.fleetName()
                                                    + ". Acceptez ou refusez la demande.",
                                            AppNotifier.Level.INFO, "DRIVER_REGISTRATION_REQUESTED",
                                            AppNotifier.refs("registrationId", id, "fleetId", fleet.fleetId())))
                                    .thenReturn(new Submitted(id, fleet.fleetName(), "PENDING"));
                        })));
    }

    private Mono<Void> assertNoDuplicate(String phone, String email, String licence) {
        Mono<Boolean> openRequest = db.sql("""
                        SELECT COUNT(*) AS n FROM fleet.driver_registrations
                        WHERE status IN ('PENDING', 'APPROVED', 'ACTIVATING')
                          AND (licence_number = :licence OR phone = :phone OR LOWER(email) = :email)
                        """)
                .bind("licence", licence)
                .bind("phone", phone == null ? NONE : phone)
                .bind("email", email == null ? NONE : email)
                .map((row, meta) -> row.get("n", Long.class))
                .one()
                .map(n -> n > 0);
        Mono<Boolean> licenceTaken = db.sql("""
                        SELECT COUNT(*) AS n FROM fleet.drivers
                        WHERE UPPER(REGEXP_REPLACE(TRIM(licence_number), '\\s+', ' ', 'g')) = :licence AND deleted_at IS NULL
                        """)
                .bind("licence", licence)
                .map((row, meta) -> row.get("n", Long.class))
                .one()
                .map(n -> n > 0);
        Mono<Boolean> emailTaken = email == null ? Mono.just(false) : db.sql("""
                        SELECT COUNT(*) AS n FROM fleet.users WHERE LOWER(email) = :email AND deleted_at IS NULL
                        """)
                .bind("email", email)
                .map((row, meta) -> row.get("n", Long.class))
                .one()
                .map(n -> n > 0);
        return Mono.zip(openRequest, licenceTaken, emailTaken).flatMap(t -> {
            if (t.getT1()) {
                return Mono.error(error("Une demande d'inscription est déjà en cours pour ces informations.",
                        HttpStatus.CONFLICT, "REG_006"));
            }
            if (t.getT2()) {
                return Mono.error(error("Ce numéro de permis est déjà enregistré. Contactez votre gestionnaire.",
                        HttpStatus.CONFLICT, "REG_007"));
            }
            if (t.getT3()) {
                return Mono.error(error("Un compte existe déjà avec cette adresse e-mail. Connectez-vous ou utilisez « Mot de passe oublié ».",
                        HttpStatus.CONFLICT, "REG_008"));
            }
            return Mono.empty();
        });
    }

    private Mono<Void> assertFleetQueue(UUID fleetId) {
        return db.sql("SELECT COUNT(*) AS n FROM fleet.driver_registrations WHERE fleet_id = :fleet AND status = 'PENDING'")
                .bind("fleet", fleetId)
                .map((row, meta) -> row.get("n", Long.class))
                .one()
                .flatMap(n -> n >= MAX_PENDING_PER_FLEET
                        ? Mono.<Void>error(error("Trop de demandes en attente pour cette flotte. Réessayez plus tard.",
                                HttpStatus.TOO_MANY_REQUESTS, "REG_009"))
                        : Mono.<Void>empty());
    }

    // ── Gestionnaire ─────────────────────────────────────────────────────────

    private static final String SELECT_REGISTRATION = """
            SELECT r.*, f.name AS fleet_name, d.onboarding_completed_at
            FROM fleet.driver_registrations r
            JOIN fleet.fleets f ON f.id = r.fleet_id
            LEFT JOIN fleet.drivers d ON d.user_id = r.driver_user_id
            """;

    private static Registration toRegistration(Readable row) {
        String status = row.get("status", String.class);
        LocalDateTime expires = row.get("code_expires_at", LocalDateTime.class);
        Integer attempts = row.get("failed_attempts", Integer.class);
        String code = row.get("activation_code", String.class);
        boolean approved = "APPROVED".equals(status);
        UUID driverUserId = row.get("driver_user_id", UUID.class);
        return new Registration(
                row.get("id", UUID.class), row.get("fleet_id", UUID.class), row.get("fleet_name", String.class),
                row.get("first_name", String.class), row.get("last_name", String.class), row.get("phone", String.class),
                row.get("email", String.class), row.get("licence_number", String.class), status,
                row.get("rejection_reason", String.class),
                approved ? formatCode(code) : null,
                approved ? expires : null,
                approved && expires != null && expires.isBefore(LocalDateTime.now()),
                approved && attempts != null && attempts >= MAX_ATTEMPTS,
                row.get("code_sent_at", LocalDateTime.class), row.get("code_sent_channels", String.class),
                driverUserId, row.get("activated_at", LocalDateTime.class),
                driverUserId == null ? null : row.get("onboarding_completed_at", LocalDateTime.class) != null,
                row.get("created_at", LocalDateTime.class), row.get("reviewed_at", LocalDateTime.class));
    }

    /** Demandes des flottes du gestionnaire : en cours, plus celles closes depuis moins de 60 jours. */
    public Flux<Registration> list(UUID actorId) {
        return accessScope.resolveLocalUserId(actorId).flatMapMany(me -> db.sql(SELECT_REGISTRATION + """
                        WHERE f.manager_id = :me
                          AND (r.status IN ('PENDING', 'APPROVED', 'ACTIVATING')
                               OR (r.driver_user_id IS NOT NULL AND d.onboarding_completed_at IS NULL)
                               OR COALESCE(r.activated_at, r.reviewed_at, r.created_at) > :since)
                        ORDER BY r.created_at DESC
                        """)
                .bind("me", me)
                .bind("since", LocalDateTime.now().minusDays(60))
                .map((row, meta) -> toRegistration(row))
                .all());
    }

    public Mono<Long> pendingCount(UUID actorId) {
        return accessScope.resolveLocalUserId(actorId).flatMap(me -> db.sql("""
                        SELECT COUNT(*) AS n FROM fleet.driver_registrations r JOIN fleet.fleets f ON f.id = r.fleet_id
                        WHERE f.manager_id = :me AND r.status = 'PENDING'
                        """)
                .bind("me", me)
                .map((row, meta) -> row.get("n", Long.class))
                .one());
    }

    private Mono<Registration> find(UUID id) {
        return db.sql(SELECT_REGISTRATION + " WHERE r.id = :id")
                .bind("id", id)
                .map((row, meta) -> toRegistration(row))
                .one()
                .switchIfEmpty(Mono.error(error("Demande d'inscription introuvable.", HttpStatus.NOT_FOUND, "REG_011")));
    }

    /** Demande d'une flotte gérée par l'acteur. */
    private Mono<Registration> findManaged(UUID actorId, UUID id) {
        return find(id).flatMap(r -> accessScope.assertFleetAccess(actorId, r.fleetId()).thenReturn(r));
    }

    private static Mono<Registration> requireStatus(Registration r, String status, String message) {
        return status.equals(r.status()) ? Mono.just(r) : Mono.error(error(message, HttpStatus.CONFLICT, "REG_012"));
    }

    /** Accepte la demande : la limite du plan est vérifiée et un code d'activation est généré. */
    public Mono<Registration> approve(UUID actorId, UUID id) {
        return findManaged(actorId, id)
                .flatMap(r -> requireStatus(r, "PENDING", "Cette demande a déjà été traitée."))
                .flatMap(r -> accessScope.resolveLocalUserId(actorId).flatMap(me -> planLimitGuard.assertCanCreateDriver(me)
                        .then(db.sql("""
                                        UPDATE fleet.driver_registrations
                                        SET status = 'APPROVED', activation_code = :code, code_expires_at = :expires,
                                            failed_attempts = 0, reviewed_by = :me, reviewed_at = :now
                                        WHERE id = :id AND status = 'PENDING'
                                        """)
                                .bind("code", newCode(8))
                                .bind("expires", LocalDateTime.now().plusHours(CODE_VALIDITY_HOURS))
                                .bind("me", me).bind("now", LocalDateTime.now()).bind("id", id)
                                .fetch().rowsUpdated())))
                .then(find(id));
    }

    public Mono<Registration> reject(UUID actorId, UUID id, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            return Mono.error(error("Indiquez le motif du refus.", HttpStatus.BAD_REQUEST, "REG_013"));
        }
        return findManaged(actorId, id)
                .flatMap(r -> "PENDING".equals(r.status()) || "APPROVED".equals(r.status()) ? Mono.just(r)
                        : Mono.error(error("Cette demande ne peut plus être refusée.", HttpStatus.CONFLICT, "REG_012")))
                .flatMap(r -> accessScope.resolveLocalUserId(actorId).flatMap(me -> db.sql("""
                                UPDATE fleet.driver_registrations
                                SET status = 'REJECTED', rejection_reason = :reason, activation_code = NULL,
                                    code_expires_at = NULL, reviewed_by = :me, reviewed_at = :now
                                WHERE id = :id AND status IN ('PENDING', 'APPROVED')
                                """)
                        .bind("reason", why.length() > 500 ? why.substring(0, 500) : why)
                        .bind("me", me).bind("now", LocalDateTime.now()).bind("id", id)
                        .fetch().rowsUpdated()))
                .then(find(id));
    }

    /** Nouveau code (expiré, trop d'essais ou communiqué par erreur) : l'ancien ne fonctionne plus. */
    public Mono<Registration> regenerateCode(UUID actorId, UUID id) {
        return findManaged(actorId, id)
                .flatMap(r -> requireStatus(r, "APPROVED", "Seule une demande acceptée a un code d'activation."))
                .flatMap(r -> db.sql("""
                                UPDATE fleet.driver_registrations
                                SET activation_code = :code, code_expires_at = :expires, failed_attempts = 0,
                                    code_sent_at = NULL, code_sent_channels = NULL
                                WHERE id = :id AND status = 'APPROVED'
                                """)
                        .bind("code", newCode(8))
                        .bind("expires", LocalDateTime.now().plusHours(CODE_VALIDITY_HOURS))
                        .bind("id", id)
                        .fetch().rowsUpdated())
                .then(find(id));
    }

    /** Envoie le code par e-mail et/ou SMS selon les coordonnées fournies par le chauffeur. */
    public Mono<SendResult> sendCode(UUID actorId, UUID id) {
        return findManaged(actorId, id)
                .flatMap(r -> requireStatus(r, "APPROVED", "Acceptez d'abord la demande."))
                .flatMap(r -> {
                    if (r.codeExpired() || r.codeLocked()) {
                        return Mono.error(error("Ce code n'est plus utilisable : générez-en un nouveau avant de l'envoyer.",
                                HttpStatus.CONFLICT, "REG_014"));
                    }
                    String text = "FleetMan : votre demande pour rejoindre la flotte " + r.fleetName()
                            + " est acceptée. Code d'activation : " + r.activationCode()
                            + " (valable jusqu'au " + r.codeExpiresAt().format(DAY_TIME) + "). "
                            + "Dans l'application FleetMan Chauffeur, choisissez « J'ai un code d'activation ».";
                    List<Mono<Delivery>> sends = new ArrayList<>();
                    if (r.email() != null) {
                        sends.add(mail.sendEmail(r.email(), "Votre code d'activation FleetMan",
                                        "Bonjour " + r.firstName() + ",\n\n" + text + "\n\nL'équipe FleetMan")
                                .onErrorReturn(false)
                                .map(ok -> new Delivery("EMAIL", maskEmail(r.email()), ok)));
                    }
                    if (r.phone() != null) {
                        sends.add(sms.sendSms(r.phone(), text)
                                .onErrorReturn(false)
                                .map(ok -> new Delivery("SMS", maskPhone(r.phone()), ok)));
                    }
                    return Flux.concat(sends).collectList().flatMap(deliveries -> {
                        String channels = String.join(",", deliveries.stream().filter(Delivery::delivered).map(Delivery::channel).toList());
                        Mono<Long> mark = channels.isEmpty() ? Mono.just(0L) : db.sql("""
                                        UPDATE fleet.driver_registrations SET code_sent_at = :now, code_sent_channels = :channels
                                        WHERE id = :id
                                        """)
                                .bind("now", LocalDateTime.now()).bind("channels", channels).bind("id", id)
                                .fetch().rowsUpdated();
                        return mark.then(find(id)).map(updated -> new SendResult(updated, deliveries));
                    });
                });
    }

    /** Code d'inscription de chaque flotte gérée ; créé au premier affichage. */
    public Flux<FleetCode> fleetCodes(UUID actorId) {
        return accessScope.resolveLocalUserId(actorId).flatMapMany(me -> db.sql("""
                        SELECT id, name, registration_code FROM fleet.fleets
                        WHERE manager_id = :me AND deleted_at IS NULL ORDER BY name
                        """)
                .bind("me", me)
                .map((row, meta) -> new FleetCode(row.get("id", UUID.class), row.get("name", String.class),
                        row.get("registration_code", String.class)))
                .all()
                .concatMap(f -> f.code() != null ? Mono.just(f) : assignFleetCode(f.fleetId(), f.fleetName())));
    }

    public Mono<FleetCode> regenerateFleetCode(UUID actorId, UUID fleetId) {
        return accessScope.assertFleetAccess(actorId, fleetId)
                .then(db.sql("SELECT name FROM fleet.fleets WHERE id = :id").bind("id", fleetId)
                        .map((row, meta) -> row.get("name", String.class)).one())
                .flatMap(name -> assignFleetCode(fleetId, name));
    }

    private Mono<FleetCode> assignFleetCode(UUID fleetId, String name) {
        String code = newCode(6);
        return db.sql("UPDATE fleet.fleets SET registration_code = :code WHERE id = :id")
                .bind("code", code).bind("id", fleetId)
                .fetch().rowsUpdated()
                .thenReturn(new FleetCode(fleetId, name, code))
                // Collision improbable sur l'index unique : on retente avec un autre code.
                .onErrorResume(e -> assignFleetCode(fleetId, name));
    }

    // ── Phase 2 : activation par le chauffeur (sans session) ───────────────

    public Mono<Activated> activate(ActivationRequest req) {
        try {
            checkPassword(req.password());
        } catch (DriverException e) {
            return Mono.error(e);
        }
        String contact = req.contact() == null ? "" : req.contact().trim();
        boolean byEmail = contact.contains("@");
        String email = byEmail ? normalizeEmail(contact) : null;
        String phone = byEmail ? null : normalizePhone(contact);
        String code = normalizeCode(req.code());
        DriverException invalid = error("Code d'activation ou coordonnées incorrects.", HttpStatus.BAD_REQUEST, "REG_015");
        if ((email == null && phone == null) || code.isEmpty()) return Mono.error(invalid);

        return db.sql("""
                        SELECT r.id, r.fleet_id, r.first_name, r.last_name, r.phone, r.email, r.licence_number,
                               r.activation_code, r.code_expires_at, r.failed_attempts, f.manager_id, f.name AS fleet_name
                        FROM fleet.driver_registrations r JOIN fleet.fleets f ON f.id = r.fleet_id
                        WHERE r.status = 'APPROVED' AND (r.phone = :phone OR LOWER(r.email) = :email)
                        ORDER BY r.reviewed_at DESC LIMIT 1
                        """)
                .bind("phone", phone == null ? NONE : phone)
                .bind("email", email == null ? NONE : email)
                .map((row, meta) -> new Pending(row.get("id", UUID.class), row.get("fleet_id", UUID.class),
                        row.get("first_name", String.class), row.get("last_name", String.class), row.get("phone", String.class),
                        row.get("email", String.class), row.get("licence_number", String.class),
                        row.get("activation_code", String.class), row.get("code_expires_at", LocalDateTime.class),
                        row.get("failed_attempts", Integer.class), row.get("manager_id", UUID.class),
                        row.get("fleet_name", String.class)))
                .one()
                .switchIfEmpty(Mono.error(invalid))
                .flatMap(p -> {
                    if (p.attempts() != null && p.attempts() >= MAX_ATTEMPTS) {
                        return Mono.error(error("Trop d'essais avec ce code. Demandez un nouveau code à votre gestionnaire.",
                                HttpStatus.TOO_MANY_REQUESTS, "REG_016"));
                    }
                    if (p.expiresAt() == null || p.expiresAt().isBefore(LocalDateTime.now())) {
                        return Mono.error(error("Ce code d'activation a expiré. Demandez un nouveau code à votre gestionnaire.",
                                HttpStatus.GONE, "REG_017"));
                    }
                    if (!code.equals(p.code())) {
                        return db.sql("UPDATE fleet.driver_registrations SET failed_attempts = failed_attempts + 1 WHERE id = :id")
                                .bind("id", p.id()).fetch().rowsUpdated()
                                .then(Mono.error(invalid));
                    }
                    // Réservation atomique : deux validations simultanées ne créent pas deux comptes.
                    return db.sql("UPDATE fleet.driver_registrations SET status = 'ACTIVATING' WHERE id = :id AND status = 'APPROVED'")
                            .bind("id", p.id()).fetch().rowsUpdated()
                            .flatMap(n -> n == 0 ? Mono.<Activated>error(invalid) : createAccount(p, req.password())
                                    .onErrorResume(e -> db.sql("UPDATE fleet.driver_registrations SET status = 'APPROVED' WHERE id = :id AND status = 'ACTIVATING'")
                                            .bind("id", p.id()).fetch().rowsUpdated()
                                            .then(Mono.error(e))));
                });
    }

    private record Pending(UUID id, UUID fleetId, String firstName, String lastName, String phone, String email,
                           String licence, String code, LocalDateTime expiresAt, Integer attempts, UUID managerId,
                           String fleetName) {}

    private Mono<Activated> createAccount(Pending p, String password) {
        String username = usernameBase(p.firstName(), p.lastName()) + "." + UUID.randomUUID().toString().substring(0, 6);
        // Le Kernel exige une adresse : à défaut, une adresse technique non routable.
        String accountEmail = p.email() != null ? p.email() : username + "@fleetman.local";
        DriverRegistrationRequest registration = new DriverRegistrationRequest(
                username, password, accountEmail, p.phone(), p.firstName(), p.lastName(), p.licence());
        return drivers.registerDriver(p.fleetId(), registration, p.managerId())
                .flatMap(d -> {
                    Mono<Long> phone = p.phone() == null ? Mono.just(0L) : db.sql("UPDATE fleet.users SET phone = :phone WHERE id = :id")
                            .bind("phone", p.phone()).bind("id", d.userId()).fetch().rowsUpdated();
                    return phone
                            .then(driverPersistence.updateEmployment(d.userId(), "ACTIVE", LocalDate.now(), null, null))
                            .then(db.sql("UPDATE fleet.drivers SET onboarding_completed_at = NULL WHERE user_id = :id")
                                    .bind("id", d.userId()).fetch().rowsUpdated())
                            .then(db.sql("""
                                            UPDATE fleet.driver_registrations
                                            SET status = 'ACTIVATED', driver_user_id = :driver, activated_at = :now,
                                                activation_code = NULL, code_expires_at = NULL
                                            WHERE id = :id
                                            """)
                                    .bind("driver", d.userId()).bind("now", LocalDateTime.now()).bind("id", p.id())
                                    .fetch().rowsUpdated())
                            .then(notifier.toUser(p.managerId(), d.userId(), "Compte chauffeur activé",
                                    p.firstName() + " " + p.lastName() + " a activé son compte (flotte " + p.fleetName()
                                            + "). Il complète maintenant son profil et ses pièces obligatoires.",
                                    AppNotifier.Level.INFO, "DRIVER_ACCOUNT_ACTIVATED",
                                    AppNotifier.refs("registrationId", p.id(), "driverId", d.userId())));
                })
                .then(Mono.defer(() -> authUseCase.login(username, password)))
                .map(auth -> new Activated(username, auth));
    }
}
