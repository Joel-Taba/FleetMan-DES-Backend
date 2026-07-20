package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MutationItem(
        UUID clientMutationId,
        UUID clientEntityId,
        String method,
        String path,
        Map<String, Object> body,
        Instant clientCreatedAt,
        List<UUID> dependsOn,
        UUID fileUploadId
) {}
