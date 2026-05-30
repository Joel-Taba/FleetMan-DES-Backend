package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import jakarta.validation.constraints.Email;

public record UpdateProfileRequest(
    // Uniquement les infos Identité (Propagées vers Auth Service)
    String firstName,
    String lastName,
    String phone,
    @Email String email
) {}