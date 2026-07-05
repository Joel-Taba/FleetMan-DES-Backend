package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.Budget;
import com.yowyob.fleet.domain.ports.out.BudgetPersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.BudgetEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.BudgetR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Adapter de persistance pour les Budgets.
 * Implémente BudgetPersistencePort via Spring Data R2DBC.
 */
@Component
@RequiredArgsConstructor
public class BudgetPersistenceAdapter implements BudgetPersistencePort {

    private final BudgetR2dbcRepository repository;

    @Override
    public Mono<Budget> save(Budget budget) {
        BudgetEntity entity = toEntity(budget);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<Budget> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<Budget> findAll() {
        return repository.findAll().map(this::toDomain);
    }

    @Override
    public Flux<Budget> findAllByManagerId(UUID managerId) {
        return repository.findAllByManagerId(managerId).map(this::toDomain);
    }

    @Override
    public Flux<Budget> findByFleetId(UUID fleetId) {
        return repository.findByFleetId(fleetId).map(this::toDomain);
    }

    @Override
    public Flux<Budget> findByVehicleId(UUID vehicleId) {
        return repository.findByVehicleId(vehicleId).map(this::toDomain);
    }

    @Override
    public Mono<Budget> findByEntityAndMonth(Budget.BudgetScope scope, UUID entityId, LocalDate month) {
        return repository.findByEntityAndMonth(scope.name(), entityId, month.withDayOfMonth(1))
                .map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsByEntityAndMonth(Budget.BudgetScope scope, UUID entityId, LocalDate month) {
        return repository.existsByEntityAndMonth(scope.name(), entityId, month.withDayOfMonth(1));
    }

    @Override
    public Flux<Budget> findActiveByManagerId(UUID managerId, LocalDate currentMonth) {
        return repository.findActiveByManagerId(managerId, currentMonth.withDayOfMonth(1))
                .map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Mono<Void> deleteById(UUID id) {
        return repository.deleteById(id);
    }

    // ── Conversion Entity → Domain ────────────────────────────────────────────

    private Budget toDomain(BudgetEntity e) {
        Budget.BudgetScope scope;
        try {
            scope = Budget.BudgetScope.valueOf(e.getScope());
        } catch (IllegalArgumentException ex) {
            scope = Budget.BudgetScope.FLEET;
        }

        Budget.AlertLevel alertLevel;
        try {
            alertLevel = Budget.AlertLevel.valueOf(e.getAlertLevel());
        } catch (IllegalArgumentException ex) {
            alertLevel = Budget.AlertLevel.NORMAL;
        }

        return new Budget(
                e.getId(),
                scope,
                e.getEntityId(),
                e.getManagerId(),
                e.getBudgetMonth(),
                e.getAmount(),
                e.getConsumed(),
                alertLevel,
                e.isAlert80Sent(),
                e.isAlert100Sent(),
                e.getNotes(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    // ── Conversion Domain → Entity ────────────────────────────────────────────

    private BudgetEntity toEntity(Budget b) {
        BudgetEntity entity = new BudgetEntity();
        entity.setId(b.getId());
        entity.setScope(b.getScope().name());
        entity.setEntityId(b.getEntityId());
        entity.setManagerId(b.getManagerId());
        entity.setBudgetMonth(b.getBudgetMonth());
        entity.setAmount(b.getAmount());
        entity.setConsumed(b.getConsumed());
        entity.setAlertLevel(b.getAlertLevel() != null ? b.getAlertLevel().name() : Budget.AlertLevel.NORMAL.name());
        entity.setAlert80Sent(b.isAlert80Sent());
        entity.setAlert100Sent(b.isAlert100Sent());
        entity.setNotes(b.getNotes());
        entity.setCreatedAt(b.getCreatedAt());
        entity.setUpdatedAt(b.getUpdatedAt());
        return entity;
    }
}
