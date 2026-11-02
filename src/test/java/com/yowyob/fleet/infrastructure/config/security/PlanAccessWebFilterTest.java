package com.yowyob.fleet.infrastructure.config.security;

import com.yowyob.fleet.domain.model.PlanFeature;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanAccessWebFilterTest {

    @Test
    void routes_mapToTheirPlanFeature() {
        assertEquals(Optional.of(PlanFeature.KPI_REPORTS), PlanAccessWebFilter.requiredFeature("/api/v1/kpis/fleet/1"));
        assertEquals(Optional.of(PlanFeature.GEOFENCING), PlanAccessWebFilter.requiredFeature("/api/v1/geofence/zones"));
        // Déclarer et suivre un incident reste ouvert à toutes les offres (sécurité).
        assertEquals(Optional.empty(), PlanAccessWebFilter.requiredFeature("/api/v1/operations/incidents"));
        assertEquals(Optional.of(PlanFeature.OPERATIONS), PlanAccessWebFilter.requiredFeature("/api/v1/operations/fuel-recharges"));
        assertEquals(Optional.of(PlanFeature.DOCUMENTS), PlanAccessWebFilter.requiredFeature("/api/v1/vehicles/1/documents"));
        assertEquals(Optional.of(PlanFeature.ALERTS), PlanAccessWebFilter.requiredFeature("/api/v1/alerts/rules"));
    }

    @Test
    void coreRoutes_andAlertFeed_needNoSpecificFeature() {
        assertEquals(Optional.empty(), PlanAccessWebFilter.requiredFeature("/api/v1/vehicles"));
        assertEquals(Optional.empty(), PlanAccessWebFilter.requiredFeature("/api/v1/alerts/events"));
        assertEquals(Optional.empty(), PlanAccessWebFilter.requiredFeature("/api/v1/budget/expenses"));
        assertEquals(Optional.empty(), PlanAccessWebFilter.requiredFeature("/api/v1/admin/super/subscriptions/1/documents"));
    }
}
