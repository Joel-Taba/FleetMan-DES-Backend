package com.yowyob.fleet.application.service;

import com.yowyob.fleet.domain.exception.TripException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TripFuelConsumptionTest {

    private static BigDecimal l(String v) {
        return new BigDecimal(v);
    }

    @Test
    void sansPleinEnRoute_consommationEgaleDepartMoinsRetour() {
        assertEquals(l("43"), TripService.computeFuelConsumed(l("64"), BigDecimal.ZERO, l("21")));
    }

    @Test
    void pleinEnRoute_estAjouteAuCarburantDisponible() {
        // 20 L au départ, plein de 50 L en route, 45 L au retour → 25 L consommés (et non -25).
        assertEquals(l("25"), TripService.computeFuelConsumed(l("20"), l("50"), l("45")));
    }

    @Test
    void retourSuperieurAuDisponible_estRefuse() {
        TripException e = assertThrows(TripException.class,
                () -> TripService.computeFuelConsumed(l("20"), BigDecimal.ZERO, l("45")));
        assertEquals("TRP_020", e.getBusinessCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    void retourEgalAuDisponible_donneZero() {
        assertEquals(0, TripService.computeFuelConsumed(l("30"), null, l("30")).signum());
    }

    @Test
    void indexManquant_pasDeCalcul() {
        assertNull(TripService.computeFuelConsumed(null, l("10"), l("5")));
        assertNull(TripService.computeFuelConsumed(l("10"), l("10"), null));
    }
}
