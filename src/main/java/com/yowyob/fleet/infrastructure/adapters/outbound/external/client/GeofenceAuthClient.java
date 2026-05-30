package com.yowyob.fleet.infrastructure.adapters.outbound.external.client;

import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;
import java.util.Map;

@HttpExchange("/api/auth")
public interface GeofenceAuthClient {

    @PostExchange("/register")
    Mono<Map<String, Object>> register(@RequestBody Map<String, Object> request);

    @PostExchange("/login")
    Mono<Map<String, Object>> login(@RequestBody Map<String, String> credentials);
}