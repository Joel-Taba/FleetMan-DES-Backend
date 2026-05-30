package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RegisterRequest(
    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    String username,
    
    @NotBlank(message = "Le mot de passe est obligatoire")
    String password,
    
    @Email(message = "Format d'email invalide")
    @NotBlank(message = "L'email est obligatoire")
    String email,
    
    @NotBlank(message = "Le téléphone est obligatoire")
    String phone,
    
    String firstName,
    String lastName,
    
    @NotNull(message = "Le rôle est obligatoire (ex: FLEET_MANAGER, FLEET_DRIVER)")
    List<String> roles
) {}