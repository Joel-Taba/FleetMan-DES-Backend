package com.yowyob.fleet.domain.model;

import java.util.UUID;

public record FleetManager(
    UUID userId,
    String companyName,
    String subscriptionLevel
) {}