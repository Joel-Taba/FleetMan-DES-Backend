package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import java.time.Instant;

/**
 * Enveloppe de réponse API alignée sur common-core du Kernel RT-Comops.
 *
 * @param <T> type des données métier
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }
}
