package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.FleetException;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Entreprise d'un utilisateur. L'administrateur EST l'entreprise (fleet.companies.id = son id) ;
 * un gestionnaire appartient à l'entreprise de l'admin qui l'a créé ; un chauffeur à celle de
 * sa flotte. Abonnement, limites et périmètre de l'admin se calculent sur l'entreprise.
 */
@Service
@RequiredArgsConstructor
public class CompanyScopeService {

    private final DatabaseClient db;
    private final AccessScopeService accessScope;

    /** Entreprise de l'acteur ; vide s'il n'en a pas (super-admin de la plateforme). */
    public Mono<UUID> resolveCompanyId(UUID actorId) {
        return accessScope.resolveLocalUserId(actorId)
                .flatMap(localId -> db.sql("""
                                SELECT COALESCE(
                                    (SELECT c.id FROM fleet.companies c WHERE c.id = :u),
                                    (SELECT fm.created_by_admin_id FROM fleet.fleet_managers fm WHERE fm.user_id = :u),
                                    (SELECT f.company_id FROM fleet.drivers d JOIN fleet.fleets f ON f.id = d.fleet_id
                                     WHERE d.user_id = :u)
                                ) AS company_id
                                """)
                        .bind("u", localId)
                        .map((row, meta) -> row.get("company_id", UUID.class))
                        .one());
    }

    /** L'acteur est-il l'administrateur (propriétaire) de cette entreprise ? */
    public Mono<Boolean> isCompanyAdmin(UUID actorId) {
        return accessScope.resolveLocalUserId(actorId)
                .flatMap(localId -> db.sql("SELECT EXISTS (SELECT 1 FROM fleet.companies WHERE id = :u) AS ok")
                        .bind("u", localId)
                        .map((row, meta) -> Boolean.TRUE.equals(row.get("ok", Boolean.class)))
                        .one())
                .defaultIfEmpty(false);
    }

    /** Entreprise de l'administrateur connecté (erreur s'il n'est pas administrateur d'une entreprise). */
    public Mono<UUID> requireOwnCompany(UUID actorId) {
        return accessScope.resolveLocalUserId(actorId)
                .filterWhen(this::isCompanyAdmin)
                .switchIfEmpty(Mono.error(FleetException.accessDenied()));
    }
}
