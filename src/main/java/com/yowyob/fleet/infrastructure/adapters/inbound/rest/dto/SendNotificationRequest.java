package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record SendNotificationRequest(
        NotificationType notificationType,
        int templateId,
        List<String> to,
        Map<String, Object> data
) {}