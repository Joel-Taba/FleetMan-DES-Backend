package com.yowyob.fleet.domain.ports.out;

import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SendNotificationRequest;
import reactor.core.publisher.Mono;

public interface SendNotificationPort {
    Mono<Boolean> sendNotification(SendNotificationRequest request);
}