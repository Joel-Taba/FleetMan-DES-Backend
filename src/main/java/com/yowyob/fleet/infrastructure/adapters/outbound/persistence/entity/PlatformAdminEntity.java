package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Marqueur "cet utilisateur est un FLEET_ADMIN" — mirroir local fiable,
 * indépendant du Kernel. Nécessaire car GET /api/users/admins du Kernel
 * renvoie un tableau JSON brut (non enveloppé ApiResponse) que le client
 * FleetMan ne sait pas désérialiser, et échoue silencieusement à chaque
 * appel (Flux.empty()) — sans ce marqueur local, aucun admin nouvellement
 * créé ne pouvait jamais apparaître dans la liste des Administrateurs.
 */
@Table(name = "platform_admins", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAdminEntity implements Persistable<UUID> {

    @Id
    @Column("user_id")
    private UUID userId;

    @Column("created_at")
    private Instant createdAt;

    @Transient
    private boolean isNew = false;

    public PlatformAdminEntity(UUID userId) {
        this.userId = userId;
        this.createdAt = Instant.now();
        this.isNew = true;
    }

    @Override
    public UUID getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return isNew || userId == null;
    }
}
