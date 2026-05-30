package com.yowyob.fleet.infrastructure.mappers;

import com.yowyob.fleet.domain.model.Fleet;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.FleetResponse;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.FleetEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FleetMapper {

    // Domain -> Entity
    // Les champs id, name, managerId sont mappés automatiquement car mêmes noms
    @Mapping(target = "phoneNumber", source = "phoneNumber") 
    FleetEntity toEntity(Fleet domain);
    
    // Entity -> Domain
    @Mapping(target = "vehicleCount", ignore = true) // Ce champ est calculé, pas dans l'entité
    Fleet toDomain(FleetEntity entity);

    // Request -> Domain
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true) // C'était "creationDate" avant, erreur ici
    @Mapping(target = "vehicleCount", ignore = true)
    Fleet toDomain(FleetRequest request);

    // Domain -> Response
    // On mappe createdAt (Instant) vers creationDate (LocalDate) pour la réponse JSON
    @Mapping(target = "creationDate", source = "createdAt")
    @Mapping(target = "managerUserId", source = "managerId")
    FleetResponse toResponse(Fleet domain);

    /**
     * Custom mapping: LocalDate to Instant
     */
    default Instant map(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * Custom mapping: Instant to LocalDate
     */
    default LocalDate map(Instant instant) {
        return instant == null ? null : LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }
}