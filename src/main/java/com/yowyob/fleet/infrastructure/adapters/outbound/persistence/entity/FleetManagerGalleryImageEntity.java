package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

/**
 * Galerie photo de l'organisation (page Organisation du manager) — même
 * modèle que {@code VehicleIllustrationImageEntity} pour les véhicules.
 */
@Table(name = "fleet_manager_gallery_images", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FleetManagerGalleryImageEntity {
    @Id
    private UUID id;

    @Column("manager_id")
    private UUID managerId;

    @Column("image_path")
    private String imagePath;
}
