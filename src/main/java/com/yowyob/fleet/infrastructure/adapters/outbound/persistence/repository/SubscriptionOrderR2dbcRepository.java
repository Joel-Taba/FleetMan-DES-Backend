package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository;

import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.SubscriptionOrderEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SubscriptionOrderR2dbcRepository extends ReactiveCrudRepository<SubscriptionOrderEntity, UUID> {

    @Query("SELECT * FROM fleet.subscription_orders WHERE company_id = :companyId ORDER BY created_at DESC")
    Flux<SubscriptionOrderEntity> findByCompanyId(UUID companyId);

    @Query("SELECT * FROM fleet.subscription_orders ORDER BY created_at DESC")
    Flux<SubscriptionOrderEntity> findAllOrderByCreatedAtDesc();

    @Query("SELECT * FROM fleet.subscription_orders WHERE payment_reference = :reference")
    Mono<SubscriptionOrderEntity> findByPaymentReference(String reference);

    /** Verrou de ligne : deux confirmations simultanées ne prolongent pas deux fois. */
    @Query("SELECT * FROM fleet.subscription_orders WHERE id = :id FOR UPDATE")
    Mono<SubscriptionOrderEntity> findByIdForUpdate(UUID id);
}
