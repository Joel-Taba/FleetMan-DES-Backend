package com.yowyob.fleet.infrastructure.mappers;

import com.yowyob.fleet.domain.model.GeofenceZone;
import com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.GeofenceZoneEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

// unmappedTargetPolicy = ReportingPolicy.IGNORE est la clé pour corriger ton erreur
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GeofenceMapper {
    
    /**
     * Mappe le domaine vers l'entité de liaison locale.
     * Note : managerId doit être présent dans l'objet domaine GeofenceZone 
     * ou passé via une méthode par défaut si nécessaire.
     */
    @Mapping(target = "zoneType", source = "zoneType")
    GeofenceZoneEntity toEntity(GeofenceZone domain);

    /**
     * Mappe l'entité vers le domaine (coquille vide pour agrégation).
     */
    @Mapping(target = "vertices", ignore = true)
    @Mapping(target = "activeDays", ignore = true)
    GeofenceZone toDomain(GeofenceZoneEntity entity);
    
    // Si GeofenceZone n'a pas de champ managerId, 
    // on peut utiliser cette astuce dans le service 
    // ou ajouter le champ au record GeofenceZone.
}