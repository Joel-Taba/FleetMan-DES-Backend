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

@Table(name = "vehicle_illustration_images", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleIllustrationImageEntity implements Persistable<UUID> {
    @Id
    private UUID id;

    @Column("vehicle_id")
    private UUID vehicleId;

    @Column("image_path")
    private String imagePath;

    // Sans ce flag, Spring Data R2DBC déduit "nouveau vs existant" du seul champ
    // id : un id pré-généré (UUID.randomUUID() assigné avant save()) le fait
    // croire à tort à une mise à jour, et tente un UPDATE sur une ligne qui
    // n'existe pas encore ("Row with Id [...] does not exist").
    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }
}
