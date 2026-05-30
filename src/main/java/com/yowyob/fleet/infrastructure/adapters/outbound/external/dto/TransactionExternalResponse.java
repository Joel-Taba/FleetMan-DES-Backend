package com.yowyob.fleet.infrastructure.adapters.outbound.external.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionExternalResponse(
        UUID id,
        UUID walletId,
        BigDecimal amount,
        String type,
        String status
) {}