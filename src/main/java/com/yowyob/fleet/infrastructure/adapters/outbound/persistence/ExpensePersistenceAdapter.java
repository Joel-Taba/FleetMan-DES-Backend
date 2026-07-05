package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.Expense;
import com.yowyob.fleet.domain.ports.out.ExpensePersistencePort;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.ExpenseEntity;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.repository.ExpenseR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Adapter de persistance pour les Dépenses.
 * Implémente ExpensePersistencePort via Spring Data R2DBC.
 * Assure la conversion Entity ↔ Domain, y compris les enums String ↔ Expense.ExpenseType, etc.
 */
@Component
@RequiredArgsConstructor
public class ExpensePersistenceAdapter implements ExpensePersistencePort {

    private final ExpenseR2dbcRepository repository;

    @Override
    public Mono<Expense> save(Expense expense) {
        ExpenseEntity entity = toEntity(expense);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setNew(true);
        }
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<Expense> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Flux<Expense> findAll() {
        return repository.findAll().map(this::toDomain);
    }

    @Override
    public Flux<Expense> findAllByManagerId(UUID managerId) {
        return repository.findAllByManagerId(managerId).map(this::toDomain);
    }

    @Override
    public Flux<Expense> findByFleetId(UUID fleetId) {
        return repository.findByFleetId(fleetId).map(this::toDomain);
    }

    @Override
    public Flux<Expense> findByVehicleId(UUID vehicleId) {
        return repository.findByVehicleId(vehicleId).map(this::toDomain);
    }

    @Override
    public Flux<Expense> findByDriverId(UUID driverId) {
        return repository.findByDriverId(driverId).map(this::toDomain);
    }

    @Override
    public Flux<Expense> findByTypeAndManagerId(Expense.ExpenseType type, UUID managerId) {
        return repository.findByTypeAndManagerId(type.name(), managerId).map(this::toDomain);
    }

    @Override
    public Flux<Expense> findByStatusAndManagerId(Expense.ExpenseStatus status, UUID managerId) {
        return repository.findByStatusAndManagerId(status.name(), managerId).map(this::toDomain);
    }

    @Override
    public Flux<Expense> findByDateRange(LocalDateTime start, LocalDateTime end, UUID managerId) {
        return repository.findByDateRangeAndManagerId(start, end, managerId).map(this::toDomain);
    }

    @Override
    public Flux<Expense> findApprovedByVehicleAndDateRange(UUID vehicleId, LocalDateTime start, LocalDateTime end) {
        return repository.findApprovedByVehicleAndDateRange(vehicleId, start, end).map(this::toDomain);
    }

    @Override
    public Flux<Expense> findApprovedByFleetAndDateRange(UUID fleetId, LocalDateTime start, LocalDateTime end) {
        return repository.findApprovedByFleetAndDateRange(fleetId, start, end).map(this::toDomain);
    }

    @Override
    public Mono<BigDecimal> getTotalApprovedByVehicleId(UUID vehicleId) {
        return repository.getTotalApprovedByVehicleId(vehicleId);
    }

    @Override
    public Mono<BigDecimal> getTotalApprovedByFleetId(UUID fleetId) {
        return repository.getTotalApprovedByFleetId(fleetId);
    }

    @Override
    public Mono<BigDecimal> getTotalApprovedByFleetAndMonth(UUID fleetId, LocalDateTime monthStart, LocalDateTime monthEnd) {
        return repository.getTotalApprovedByFleetAndMonth(fleetId, monthStart, monthEnd);
    }

    @Override
    public Mono<BigDecimal> getTotalApprovedByVehicleAndMonth(UUID vehicleId, LocalDateTime monthStart, LocalDateTime monthEnd) {
        return repository.getTotalApprovedByVehicleAndMonth(vehicleId, monthStart, monthEnd);
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

    private Expense toDomain(ExpenseEntity e) {
        Expense.ExpenseType type;
        try {
            type = Expense.ExpenseType.valueOf(e.getExpenseType());
        } catch (IllegalArgumentException ex) {
            type = Expense.ExpenseType.OTHER;
        }

        Expense.SourceType sourceType;
        try {
            sourceType = Expense.SourceType.valueOf(e.getSourceType());
        } catch (IllegalArgumentException ex) {
            sourceType = Expense.SourceType.MANUAL;
        }

        Expense expense = new Expense(
                e.getId(),
                type,
                e.getAmount(),
                e.getDescription(),
                e.getExpenseDate(),
                sourceType,
                e.getSourceId(),
                e.getVehicleId(),
                e.getVehicleRegistration(),
                e.getFleetId(),
                e.getManagerId(),
                e.getDriverId(),
                e.getDriverFullName(),
                e.getCreatedAt()
        );

        // Restauration de l'état de validation
        if (e.getStatus() != null) {
            try {
                expense.setStatus(Expense.ExpenseStatus.valueOf(e.getStatus()));
            } catch (IllegalArgumentException ex) {
                expense.setStatus(Expense.ExpenseStatus.PENDING);
            }
        }
        expense.setValidatedAt(e.getValidatedAt());
        expense.setValidatedBy(e.getValidatedBy());
        expense.setRejectionReason(e.getRejectionReason());

        return expense;
    }

    // ── Conversion Domain → Entity ────────────────────────────────────────────

    private ExpenseEntity toEntity(Expense e) {
        ExpenseEntity entity = new ExpenseEntity();
        entity.setId(e.getId());
        entity.setExpenseType(e.getExpenseType().name());
        entity.setAmount(e.getAmount());
        entity.setDescription(e.getDescription());
        entity.setExpenseDate(e.getExpenseDate());
        entity.setStatus(e.getStatus() != null ? e.getStatus().name() : Expense.ExpenseStatus.PENDING.name());
        entity.setSourceType(e.getSourceType().name());
        entity.setSourceId(e.getSourceId());
        entity.setRejectionReason(e.getRejectionReason());
        entity.setValidatedAt(e.getValidatedAt());
        entity.setValidatedBy(e.getValidatedBy());
        entity.setVehicleId(e.getVehicleId());
        entity.setVehicleRegistration(e.getVehicleRegistration());
        entity.setFleetId(e.getFleetId());
        entity.setManagerId(e.getManagerId());
        entity.setDriverId(e.getDriverId());
        entity.setDriverFullName(e.getDriverFullName());
        entity.setCreatedAt(e.getCreatedAt());
        return entity;
    }
}
