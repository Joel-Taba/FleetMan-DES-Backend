package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity.resources;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

@Table(name = "fuel_types", schema = "fleet")
@Data @NoArgsConstructor @AllArgsConstructor
public class FuelTypeEntity implements Persistable<UUID> {
    @Id private UUID id;
    private String code;
    private String label;
    private String description;
    @Transient private boolean isNew = false;
    @Override public boolean isNew() { return isNew || id == null; }
}