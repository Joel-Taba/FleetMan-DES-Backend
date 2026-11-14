package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exceptions métier du module Mission.
 * Couvre les agrégats : Mission, Preuve de livraison, Tournée.
 *
 * <p>Codes d'erreur :</p>
 * <ul>
 *   <li>{@code MSN_001} – Mission introuvable</li>
 *   <li>{@code MSN_002} – Véhicule non disponible pour cette mission</li>
 *   <li>{@code MSN_003} – Conducteur non disponible pour cette mission</li>
 *   <li>{@code MSN_004} – Transition de statut invalide pour la mission</li>
 *   <li>{@code MSN_005} – Mission déjà active ou clôturée</li>
 *   <li>{@code MSN_006} – Preuve de livraison introuvable</li>
 *   <li>{@code MSN_007} – Preuve déjà traitée (acceptée ou rejetée)</li>
 *   <li>{@code MSN_008} – Hash d'intégrité manquant pour la vérification</li>
 *   <li>{@code MSN_009} – Altération de preuve détectée</li>
 *   <li>{@code MSN_010} – Mission liée à une livraison externe non modifiable</li>
 *   <li>{@code MSN_011} – Date de fin prévue antérieure à la date de début</li>
 *   <li>{@code MSN_012} – Tournée non planifiable : contraintes infaisables</li>
 * </ul>
 */
public class MissionException extends DomainException {

    /**
     * Codes d'erreur métier associés aux exceptions de mission.
     */
    public enum ErrorCode {
        /** Mission introuvable. */
        MSN_001("Mission introuvable"),
        /** Véhicule non disponible pour cette mission. */
        MSN_002("Véhicule non disponible pour cette mission"),
        /** Conducteur non disponible pour cette mission. */
        MSN_003("Conducteur non disponible pour cette mission"),
        /** Transition de statut invalide pour la mission. */
        MSN_004("Transition de statut invalide pour la mission"),
        /** Mission déjà active ou clôturée. */
        MSN_005("Mission déjà active ou clôturée"),
        /** Preuve de livraison introuvable. */
        MSN_006("Preuve de livraison introuvable"),
        /** Preuve déjà traitée (acceptée ou rejetée). */
        MSN_007("Preuve déjà traitée (acceptée ou rejetée)"),
        /** Hash d'intégrité manquant pour la vérification. */
        MSN_008("Hash d'intégrité manquant pour la vérification"),
        /** Altération de preuve détectée. */
        MSN_009("Altération de preuve détectée"),
        /** Mission liée à une livraison externe non modifiable. */
        MSN_010("Mission liée à une livraison externe non modifiable"),
        /** Date de fin prévue antérieure à la date de début. */
        MSN_011("Date de fin prévue antérieure à la date de début"),
        /** Tournée non planifiable : contraintes infaisables. */
        MSN_012("Tournée non planifiable : contraintes infaisables");

        private final String defaultMessage;

        ErrorCode(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }

        /**
         * Retourne le message par défaut associé à ce code d'erreur.
         *
         * @return le message par défaut
         */
        public String getDefaultMessage() {
            return defaultMessage;
        }
    }

    /**
     * Constructeur protégé à usage interne des fabriques statiques.
     *
     * @param message      message descriptif de l'erreur
     * @param status       statut HTTP associé
     * @param businessCode code métier de l'erreur (ex. {@code "MSN_001"})
     */
    protected MissionException(String message, HttpStatus status, String businessCode) {
        super(message, status, businessCode);
    }

    // ── Mission ───────────────────────────────────────────────────────────────

    /**
     * Lève une exception lorsque la mission est introuvable.
     *
     * @param id identifiant de la mission introuvable
     * @return l'exception {@code MSN_001}
     */
    public static MissionException missionNotFound(UUID id) {
        return new MissionException(
            "Mission introuvable (ID: " + id + ").",
            HttpStatus.NOT_FOUND, ErrorCode.MSN_001.name()
        );
    }

    /**
     * Lève une exception lorsque le véhicule n'est pas disponible pour la mission.
     *
     * @param vehicleId identifiant du véhicule concerné
     * @return l'exception {@code MSN_002}
     */
    public static MissionException vehicleUnavailable(UUID vehicleId) {
        return new MissionException(
            "Véhicule non disponible pour cette mission (ID: " + vehicleId + ").",
            HttpStatus.CONFLICT, ErrorCode.MSN_002.name()
        );
    }

    /**
     * Lève une exception lorsque le conducteur n'est pas disponible pour la mission.
     *
     * @param driverId identifiant du conducteur concerné
     * @return l'exception {@code MSN_003}
     */
    public static MissionException driverUnavailable(UUID driverId) {
        return new MissionException(
            "Conducteur non disponible pour cette mission (ID: " + driverId + ").",
            HttpStatus.CONFLICT, ErrorCode.MSN_003.name()
        );
    }

    /**
     * Lève une exception lorsque la transition de statut de mission est invalide.
     *
     * @param from statut source
     * @param to   statut cible
     * @return l'exception {@code MSN_004}
     */
    public static MissionException invalidStatusTransition(String from, String to) {
        return new MissionException(
            "Transition de statut invalide pour la mission : " + from + " → " + to + ".",
            HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.MSN_004.name()
        );
    }

    /**
     * Lève une exception lorsque la mission est déjà active ou clôturée.
     *
     * @param id identifiant de la mission
     * @return l'exception {@code MSN_005}
     */
    public static MissionException missionAlreadyActiveOrClosed(UUID id) {
        return new MissionException(
            "Mission déjà active ou clôturée (ID: " + id + ").",
            HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.MSN_005.name()
        );
    }

    // ── Preuve de livraison ───────────────────────────────────────────────────

    /**
     * Lève une exception lorsque la preuve de livraison est introuvable.
     *
     * @param id identifiant de la preuve introuvable
     * @return l'exception {@code MSN_006}
     */
    public static MissionException deliveryProofNotFound(UUID id) {
        return new MissionException(
            "Preuve de livraison introuvable (ID: " + id + ").",
            HttpStatus.NOT_FOUND, ErrorCode.MSN_006.name()
        );
    }

    /**
     * Lève une exception lorsque la preuve a déjà été traitée (acceptée ou rejetée).
     *
     * @param id identifiant de la preuve
     * @return l'exception {@code MSN_007}
     */
    public static MissionException proofAlreadyProcessed(UUID id) {
        return new MissionException(
            "Preuve déjà traitée (acceptée ou rejetée) (ID: " + id + ").",
            HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.MSN_007.name()
        );
    }

    /**
     * Lève une exception lorsque le hash d'intégrité est manquant pour la vérification.
     *
     * @return l'exception {@code MSN_008}
     */
    public static MissionException missingIntegrityHash() {
        return new MissionException(
            "Hash d'intégrité manquant pour la vérification de la preuve.",
            HttpStatus.BAD_REQUEST, ErrorCode.MSN_008.name()
        );
    }

    /**
     * Lève une exception lorsqu'une altération de la preuve est détectée.
     *
     * @param id identifiant de la preuve altérée
     * @return l'exception {@code MSN_009}
     */
    public static MissionException proofTamperingDetected(UUID id) {
        return new MissionException(
            "Altération de preuve détectée (ID: " + id + ").",
            HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.MSN_009.name()
        );
    }

    // ── Contraintes externes et planification ─────────────────────────────────

    /**
     * Lève une exception lorsque la mission est liée à une livraison externe et non modifiable.
     *
     * @param id identifiant de la mission
     * @return l'exception {@code MSN_010}
     */
    public static MissionException linkedToExternalDelivery(UUID id) {
        return new MissionException(
            "Mission liée à une livraison externe non modifiable (ID: " + id + ").",
            HttpStatus.FORBIDDEN, ErrorCode.MSN_010.name()
        );
    }

    /**
     * Lève une exception lorsque la date de fin prévue est antérieure à la date de début.
     *
     * @return l'exception {@code MSN_011}
     */
    public static MissionException endDateBeforeStartDate() {
        return new MissionException(
            "Date de fin prévue antérieure à la date de début.",
            HttpStatus.BAD_REQUEST, ErrorCode.MSN_011.name()
        );
    }

    /**
     * Lève une exception lorsque la tournée ne peut pas être planifiée en raison de contraintes infaisables.
     *
     * @return l'exception {@code MSN_012}
     */
    public static MissionException routeInfeasible() {
        return new MissionException(
            "Tournée non planifiable : contraintes infaisables.",
            HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.MSN_012.name()
        );
    }
}
