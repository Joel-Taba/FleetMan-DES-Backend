package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table(name = "vehicle_types", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleTypeEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    private String code;       // ex: CAR
    private String label;      // ex: Voiture
    private String description;

    /** Catégorie de permis exigée pour conduire ce type de véhicule (ex. C pour un camion). */
    @Column("required_license_category")
    private String requiredLicenseCategory;

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }
}