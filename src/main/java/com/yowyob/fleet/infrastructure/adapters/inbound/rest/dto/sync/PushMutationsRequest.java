package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.sync;

import java.util.List;

public record PushMutationsRequest(List<MutationItem> mutations) {}
