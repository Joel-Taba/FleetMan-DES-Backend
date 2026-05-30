package com.yowyob.fleet.infrastructure.adapters.outbound.external.dto;

import java.util.UUID;

public record WalletCreationRequest(
        UUID ownerId,
        String ownerName
) {}