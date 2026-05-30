package com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO pour la création simultanée d'un utilisateur Auth et d'un profil Chauffeur.
 * Note : La photo n'est plus demandée ici, le chauffeur doit l'uploader lui-même via /account/picture.
 */
public record DriverRegistrationRequest(
    
    // --- Informations pour l'Auth Service ---
    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    String username,

    @NotBlank(message = "Le mot de passe est obligatoire")
    String password,

    @Email(message = "Format d'email invalide")
    @NotBlank(message = "L'email est obligatoire")
    String email,

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    String phone,

    @NotBlank(message = "Le prénom est obligatoire")
    String firstName,

    @NotBlank(message = "Le nom est obligatoire")
    String lastName,

    // --- Informations métier ---
    @NotBlank(message = "Le numéro de permis de conduire est obligatoire")
    String licenceNumber
) {}