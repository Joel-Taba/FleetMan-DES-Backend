package com.yowyob.fleet.infrastructure.adapters.outbound.external.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletExternalResponse(
        UUID id,
        UUID ownerId,
        String ownerName,
        BigDecimal balance
) {}