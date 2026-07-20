package com.yowyob.fleet.infrastructure.adapters.outbound.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

@Table(name = "operational_parameters", schema = "fleet")
@Data @NoArgsConstructor @AllArgsConstructor
public class OperationalParameterEntity {
    // NB: cette entité doit correspondre EXACTEMENT aux colonnes réelles de
    // fleet.operational_parameters (migration 004/012). Elle contenait
    // auparavant trip_id/current_location/statut qui n'existent pas (ou plus)
    // en base — Spring Data R2DBC génère le SELECT à partir de TOUS les champs
    // de l'entité, donc le moindre champ orphelin fait échouer TOUTE lecture
    // (findByVehicleId) avec un BadSqlGrammarException, y compris pendant
    // l'enregistrement du retour d'un trajet.
    @Id
    private UUID id;

    @Column("vehicle_id")
    private UUID vehicleId;

    @Column("status")
    private Boolean statut;

    @Column("current_speed")
    private BigDecimal currentSpeed;
    
    @Column("fuel_level")
    private String fuelLevel;
    
    private BigDecimal mileage;
    
    @Column("odometer_reading")
    private BigDecimal odometerReading;
    
    private BigDecimal bearing;
    
    private Instant timestamp;
}