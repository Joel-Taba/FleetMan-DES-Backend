package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.domain.model.GeofencePoint;
import java.util.List;

public interface DistanceCalculatorPort {
    /**
     * Calcule la distance totale en KM à partir d'une liste ordonnée de points GPS.
     */
    Double calculateTotalDistanceKm(List<GeofencePoint> points);
}