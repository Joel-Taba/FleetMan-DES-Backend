package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync;

import java.time.Instant;

public record SyncStatusResponse(
        Instant serverTime,
        String recommendedCursor,
        String apiVersion
) {}
