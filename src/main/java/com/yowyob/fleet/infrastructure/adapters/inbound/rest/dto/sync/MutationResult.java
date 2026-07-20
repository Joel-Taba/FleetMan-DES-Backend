package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync;

import java.util.Map;
import java.util.UUID;

public record MutationResult(
        UUID clientMutationId,
        String status,
        Integer httpStatus,
        UUID entityId,
        Map<String, Object> responseBody,
        String errorCode,
        String errorMessage
) {}
