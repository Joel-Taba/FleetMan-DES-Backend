package com.yowyob.fleet.infrastructure.adapters.outbound.external.client;

import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.SendNotificationRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import reactor.core.publisher.Mono;

@HttpExchange("/api/v1/notifications")
public interface NotificationApiClient {

    @PostExchange("/send")
    Mono<Void> sendNotification(@RequestBody SendNotificationRequest request);
}