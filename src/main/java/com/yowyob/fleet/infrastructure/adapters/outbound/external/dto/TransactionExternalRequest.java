package com.yowyob.fleet.infrastructure.adapters.outbound.external.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionExternalRequest(
        UUID walletId,
        BigDecimal amount,
        String type // "PAYMENT" ou "RECHARGE"
) {}