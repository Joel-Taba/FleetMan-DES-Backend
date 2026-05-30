package com.yowyob.fleet.domain.model;

import lombok.Builder;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record Notification(
    UUID id,
    UUID userId,
    String title,
    String message,
    String type,
    boolean isRead,
    LocalDateTime createdAt,
    String dataJson
) {}