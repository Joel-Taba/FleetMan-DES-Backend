package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.ports.in.ManageDeliveryProofUseCase;
import com.yowyob.fleet.domain.ports.out.AuthPort;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DeliveryProofRequest;
import com.yowyob.fleet.infrastructure.adapters.inbound.rest.dto.DeliveryProofResponse;
import com.yowyob.fleet.infrastructure.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_PROOFS)
@SecurityRequirement(name = "bearerAuth")
public class DeliveryProofController {

    private final ManageDeliveryProofUseCase proofUseCase;

    private UUID getUserId(Authentication auth) {
        if (auth.getPrincipal() instanceof AuthPort.UserDetail userDetail) {
            return userDetail.id();
        }
        return UUID.fromString(auth.getName());
    }

    @PostMapping("/{missionId}/proof")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FLEET_DRIVER', 'FLEET_MANAGER', 'FLEET_ADMIN')")
    @Operation(summary = "Déposer une preuve de livraison",
               description = "Permet au conducteur d'enregistrer une preuve (photo, signature, QR scan) avec horodatage, position GPS et hash cryptographique.")
    public Mono<DeliveryProofResponse> submitProof(
            @PathVariable UUID missionId,
            @Valid @RequestBody DeliveryProofRequest request,
            Authentication auth) {

        UUID driverId = getUserId(auth);

        ManageDeliveryProofUseCase.SubmitProofCommand cmd = new ManageDeliveryProofUseCase.SubmitProofCommand(
                missionId,
                driverId,
                request.type(),
                request.fileId(),
                request.fileUrl(),
                request.integrityHash(),
                request.recipientName(),
                request.recipientSignature(),
                request.latitude(),
                request.longitude(),
                request.notes(),
                null
        );

        return proofUseCase.submitProof(cmd).map(DeliveryProofResponse::fromDomain);
    }

    @GetMapping("/{missionId}/proof")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_DRIVER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Consulter la preuve d'une mission", description = "Retourne la preuve de livraison enregistrée pour une mission.")
    public Mono<DeliveryProofResponse> getProofByMission(@PathVariable UUID missionId) {
        return proofUseCase.getProofByMissionId(missionId).map(DeliveryProofResponse::fromDomain);
    }

    @PostMapping("/proofs/{proofId}/accept")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Accepter une preuve de livraison",
               description = "Valide formellement la preuve. Déclenche la notification vers le cœur central (G1) pour autoriser la libération du règlement de test.")
    public Mono<DeliveryProofResponse> acceptProof(
            @PathVariable UUID proofId,
            Authentication auth) {
        UUID reviewerId = getUserId(auth);
        return proofUseCase.acceptProof(proofId, reviewerId).map(DeliveryProofResponse::fromDomain);
    }

    @PostMapping("/proofs/{proofId}/reject")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Rejeter une preuve de livraison",
               description = "Rejette la preuve avec motif explicite. Déclenche une alerte de litige/incident vers TiiBnTick.")
    public Mono<DeliveryProofResponse> rejectProof(
            @PathVariable UUID proofId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        String reason = body.getOrDefault("reason", "Preuve non conforme ou illisible");
        UUID reviewerId = getUserId(auth);
        return proofUseCase.rejectProof(proofId, reviewerId, reason).map(DeliveryProofResponse::fromDomain);
    }

    @PostMapping("/proofs/{proofId}/verify-integrity")
    @PreAuthorize("hasAnyRole('FLEET_MANAGER', 'FLEET_ADMIN', 'FLEET_SUPER_ADMIN')")
    @Operation(summary = "Vérifier l'intégrité d'une preuve (R9)",
               description = "Compare le hash HMAC-SHA256 stocké avec un hash recalculé. Si altéré, bascule le statut en TAMPERED et bloque l'opération.")
    public Mono<Map<String, Object>> verifyIntegrity(
            @PathVariable UUID proofId,
            @RequestBody Map<String, String> body) {
        String calculatedHash = body.get("hash");
        return proofUseCase.verifyProofIntegrity(proofId, calculatedHash)
                .map(isValid -> Map.<String, Object>of(
                        "proofId", proofId,
                        "valid", isValid,
                        "message", "Intégrité vérifiée avec succès."
                ));
    }
}

