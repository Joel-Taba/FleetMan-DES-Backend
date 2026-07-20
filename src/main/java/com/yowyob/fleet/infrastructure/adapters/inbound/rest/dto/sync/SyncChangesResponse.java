package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync;

import java.time.Instant;
import java.util.List;

public record SyncChangesResponse(
        String cursor,
        Instant serverTime,
        boolean full,
        boolean hasMore,
        List<SyncChange> changes,
        List<DeletedEntityRef> deletedIds
) {}
