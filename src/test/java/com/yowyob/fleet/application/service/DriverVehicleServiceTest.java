package com.yowyob.fleet.application.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DriverVehicleServiceTest {

    private static DriverVehicleService.Row row(UUID id, String plate, String brand, String model, String type, String reason) {
        return new DriverVehicleService.Row(id, plate, brand, model, type, reason);
    }

    @Test
    void group_dedupliqueParVehicule_etGardeLOrdre() {
        UUID regular = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        List<DriverVehicleService.DriverVehicle> out = DriverVehicleService.group(List.of(
                row(regular, "DL-201-CC", "Toyota", "Hilux", "Pick-up", "REGULAR"),
                row(regular, "DL-201-CC", "Toyota", "Hilux", "Pick-up", "TRIP"),
                row(other, "LT-999-AA", null, null, "Camion", "SHIFT"),
                row(regular, "DL-201-CC", "Toyota", "Hilux", "Pick-up", "SHIFT"),
                row(other, "LT-999-AA", null, null, "Camion", "SHIFT")));

        assertThat(out).hasSize(2);
        assertThat(out.get(0).vehicleId()).isEqualTo(regular);
        assertThat(out.get(0).label()).isEqualTo("Toyota Hilux");
        assertThat(out.get(0).reasons()).containsExactly("REGULAR", "TRIP", "SHIFT");
        assertThat(out.get(1).vehicleId()).isEqualTo(other);
        assertThat(out.get(1).label()).isEqualTo("Camion");
        assertThat(out.get(1).reasons()).containsExactly("SHIFT");
    }

    @Test
    void label_marqueSeule_ouVide() {
        UUID id = UUID.randomUUID();
        assertThat(DriverVehicleService.label(row(id, "X", "Renault", " ", null, "SHIFT"))).isEqualTo("Renault");
        assertThat(DriverVehicleService.label(row(id, "X", null, null, null, "SHIFT"))).isEmpty();
    }

    @Test
    void group_listeVide() {
        assertThat(DriverVehicleService.group(List.of())).isEmpty();
    }
}
