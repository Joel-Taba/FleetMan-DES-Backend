package com.yowyob.fleet.infrastructure.adapters.outbound.external;

import com.yowyob.fleet.domain.model.GeofencePoint;
import com.yowyob.fleet.domain.ports.out.DistanceCalculatorPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FakeDistanceAdapter implements DistanceCalculatorPort {

    @Override
    public Double calculateTotalDistanceKm(List<GeofencePoint> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            totalDistance += haversine(
                points.get(i).latitude(), points.get(i).longitude(),
                points.get(i+1).latitude(), points.get(i+1).longitude()
            );
        }
        // Arrondi à 2 décimales
        return Math.round(totalDistance * 100.0) / 100.0;
    }

    // Formule standard pour distance entre 2 points GPS
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Rayon de la terre en km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}