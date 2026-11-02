package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.model.DocumentCatalog;
import com.yowyob.fleet.domain.model.DocumentCatalog.TargetType;
import com.yowyob.fleet.domain.model.DocumentRules;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DocumentEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.DocumentR2dbcRepository;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Pièces exigées d'une cible (socle légal + politique de l'entreprise) et écarts constatés. */
@Service
@RequiredArgsConstructor
public class DocumentComplianceService {

    private final DocumentR2dbcRepository documents;
    private final DatabaseClient db;

    public Mono<Set<String>> requiredTypes(TargetType type, UUID targetId) {
        return companyOf(type, targetId)
                .flatMap(companyId -> requiredTypesOfCompany(type, companyId))
                .defaultIfEmpty(new LinkedHashSet<>(DocumentCatalog.legalTypes(type)));
    }

    public Mono<Set<String>> requiredTypesOfCompany(TargetType type, UUID companyId) {
        return db.sql("SELECT doc_type FROM fleet.document_policies "
                        + "WHERE company_id = :c AND target_type = :t AND mandatory")
                .bind("c", companyId)
                .bind("t", type.name())
                .map(row -> row.get("doc_type", String.class))
                .all()
                .collectList()
                .map(extra -> {
                    Set<String> required = new LinkedHashSet<>(DocumentCatalog.legalTypes(type));
                    required.addAll(extra);
                    return required;
                });
    }

    public Mono<UUID> companyOf(TargetType type, UUID targetId) {
        String sql = switch (type) {
            case VEHICLE -> "SELECT f.company_id AS c FROM fleet.vehicles v JOIN fleet.fleets f ON f.id = v.fleet_id WHERE v.id = :id";
            case DRIVER -> "SELECT f.company_id AS c FROM fleet.drivers d JOIN fleet.fleets f ON f.id = d.fleet_id WHERE d.user_id = :id";
            case MANAGER -> "SELECT created_by_admin_id AS c FROM fleet.fleet_managers WHERE user_id = :id";
        };
        return db.sql(sql).bind("id", targetId).map(row -> row.get("c", UUID.class)).one();
    }

    /** Écarts au {@code validOn} : pièces exigées absentes, non approuvées ou expirées. */
    public Mono<List<DocumentRules.Gap>> gaps(TargetType type, UUID targetId, LocalDate validOn) {
        return Mono.zip(requiredTypes(type, targetId),
                        documents.findActive(type.name(), targetId).map(DocumentEntity::toPiece).collectList())
                .map(t -> DocumentRules.gaps(t.getT2(), t.getT1(), validOn));
    }

    /** Permis approuvés et en vigueur du chauffeur. */
    public Flux<DocumentEntity> licensesInForce(UUID driverId) {
        return documents.findCurrent(TargetType.DRIVER.name(), driverId, DocumentCatalog.DRIVING_LICENSE)
                .filter(d -> "APPROVED".equals(d.getReviewStatus()));
    }

    public static String describe(TargetType type, DocumentRules.Gap gap) {
        String label = DocumentCatalog.label(type, gap.docType());
        return gap.missing() ? label + " absente ou non approuvée" : label + " expirée le " + gap.expiredOn();
    }
}
