package com.yowyob.fleet.application.service;

import com.yowyob.fleet.application.service.VehicleOperationalJournal.Snapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Anomalies d'un relevé terrain signalées au gestionnaire. */
class VehicleOperationalJournalTest {

    private static Snapshot snap(Integer km, String fuel) {
        return new Snapshot(km == null ? null : BigDecimal.valueOf(km), fuel);
    }

    @Test
    void readsGaugePercentages() {
        assertThat(VehicleOperationalJournal.fuelPct("62%")).isEqualTo(62);
        assertThat(VehicleOperationalJournal.fuelPct(" 10 ")).isEqualTo(10);
        assertThat(VehicleOperationalJournal.fuelPct("plein")).isNull();
    }

    @Test
    void normalReadingHasNoAnomaly() {
        assertThat(VehicleOperationalJournal.anomalies(snap(1000, "60%"), snap(1120, "50%"))).isEmpty();
    }

    @Test
    void lowFuelIsReportedWhenCrossingTheThresholdOrDroppingFurther() {
        assertThat(VehicleOperationalJournal.anomalies(snap(1000, "40%"), snap(1000, "10%")))
                .containsExactly(VehicleOperationalJournal.FUEL_LOW);
        assertThat(VehicleOperationalJournal.anomalies(snap(1000, "10%"), snap(1000, "5%")))
                .containsExactly(VehicleOperationalJournal.FUEL_LOW);
        // Même niveau déjà signalé : pas de nouvelle alerte.
        assertThat(VehicleOperationalJournal.anomalies(snap(1000, "10%"), snap(1010, "10%"))).isEmpty();
    }

    @Test
    void bigOdometerJumpIsSuspicious() {
        assertThat(VehicleOperationalJournal.anomalies(snap(1000, "50%"), snap(1000 + VehicleOperationalJournal.ODOMETER_JUMP_KM + 1, "50%")))
                .containsExactly(VehicleOperationalJournal.ODOMETER_JUMP);
        assertThat(VehicleOperationalJournal.anomalies(snap(null, "50%"), snap(90000, "50%"))).isEmpty();
    }
}
