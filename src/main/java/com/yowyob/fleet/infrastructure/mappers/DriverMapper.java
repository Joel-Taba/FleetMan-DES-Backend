package com.yowyob.fleet.infrastructure.mappers;

import com.yowyob.fleet.domain.model.Driver;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.DriverEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface DriverMapper {

    @Mapping(target = "status", source = "status", qualifiedByName = "booleanToStatusString")
    @Mapping(target = "new", ignore = true)
    DriverEntity toEntity(Driver domain);

    @Mapping(target = "status", source = "status", qualifiedByName = "statusStringToBoolean")
    Driver toDomain(DriverEntity entity);

    @Named("booleanToStatusString")
    default String booleanToStatusString(Boolean status) {
        if (status == null) return "ACTIVE";
        return status ? "ACTIVE" : "INACTIVE";
    }

    @Named("statusStringToBoolean")
    default Boolean statusStringToBoolean(String status) {
        if (status == null) return true;
        return "ACTIVE".equalsIgnoreCase(status);
    }
}