package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

public record UpdateManagerRequest(
        String companyName,
        String phone,
        String address,
        String city,
        String logoUrl) {
}