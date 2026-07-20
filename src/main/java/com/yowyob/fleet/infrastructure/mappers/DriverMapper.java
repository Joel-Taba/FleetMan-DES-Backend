package com.yowyob.fleet.infrastructure.mappers;

import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Driver.status()/DriverEntity.status sont tous deux des String ("ACTIVE",
 * "INACTIVE", ...) — un précédent round-trip via Boolean ici convertissait
 * silencieusement toute valeur en "true"/"false" à la lecture (String.valueOf
 * du Boolean) et cassait la sauvegarde d'un nouveau chauffeur (toute chaîne
 * autre que "true" devenait "INACTIVE" via Boolean.valueOf). Mapping direct.
 */
@Mapper(componentModel = "spring")
public interface DriverMapper {

    @Mapping(target = "new", ignore = true)
    DriverEntity toEntity(Driver domain);

    Driver toDomain(DriverEntity entity);
}