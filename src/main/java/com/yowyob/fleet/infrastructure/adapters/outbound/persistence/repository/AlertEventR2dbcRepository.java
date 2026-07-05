package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.AlertEventEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface AlertEventR2dbcRepository extends ReactiveCrudRepository<AlertEventEntity, UUID> {

    @Query("SELECT * FROM fleet.alert_events WHERE manager_id = :managerId AND read_status = 'UNREAD' ORDER BY sent_at DESC")
    Flux<AlertEventEntity> findUnreadByManagerId(UUID managerId);

    @Query("SELECT * FROM fleet.alert_events WHERE manager_id = :managerId ORDER BY sent_at DESC LIMIT 100")
    Flux<AlertEventEntity> findAllByManagerId(UUID managerId);

    @Query("SELECT COUNT(*) FROM fleet.alert_events WHERE manager_id = :managerId AND read_status = 'UNREAD'")
    Mono<Long> countUnreadByManagerId(UUID managerId);

    /**
     * Marque toutes les notifications UNREAD comme READ.
     * Retourne le nombre de lignes modifiées.
     */
    @Modifying
    @Query("UPDATE fleet.alert_events SET read_status = 'READ', read_at = now() WHERE manager_id = :managerId AND read_status = 'UNREAD'")
    Mono<Long> markAllAsReadByManagerId(UUID managerId);
}
