package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import jakarta.validation.constraints.Email;

public record UpdateDriverRequest(
        String firstName,
        String lastName,
        String phone,
        @Email String email,
        String licenceNumber) {
}
