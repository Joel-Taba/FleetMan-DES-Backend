package com.yowyob.fleet.infrastructure.adapters.outbound.persistence;

import com.yowyob.fleet.domain.model.GeofencePoint;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisTelemetryAdapter {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    // Clé : trip:{tripId}:path
    private String getKey(UUID tripId) {
        return "trip:" + tripId + ":path";
    }

    public Mono<Void> addPoint(UUID tripId, Double lat, Double lng) {
        // On stocke sous forme d'objet simple (sérialisé en JSON par la config Redis)
        GeofencePoint point = new GeofencePoint(null, lat, lng, null);
        String key = getKey(tripId);
        
        return redisTemplate.opsForList().rightPush(key, point)
                // On met une expiration de sécurité (24h) pour ne pas polluer Redis si le trip plante
                .then(redisTemplate.expire(key, Duration.ofHours(24)))
                .then();
    }

    public Flux<GeofencePoint> getTripPath(UUID tripId) {
        return redisTemplate.opsForList().range(getKey(tripId), 0, -1)
                .cast(GeofencePoint.class);
    }

    public Mono<Void> clearTripPath(UUID tripId) {
        return redisTemplate.delete(getKey(tripId)).then();
    }
}