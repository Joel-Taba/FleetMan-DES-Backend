package com.yowyob.fleet.domain.model;

import lombok.Builder;
import java.util.UUID;

@Builder
public record NotificationSettings(
    UUID userId,
    boolean emailEnabled,
    boolean smsEnabled,
    boolean pushEnabled,
    boolean whatsappEnabled
) {
    // Par défaut, si l'user n'a jamais configuré
    public static NotificationSettings defaults(UUID userId) {
        return new NotificationSettings(userId, true, false, true, false); 
    }
}