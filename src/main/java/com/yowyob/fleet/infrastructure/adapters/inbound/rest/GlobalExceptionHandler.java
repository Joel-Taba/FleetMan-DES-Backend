package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.exception.DomainException;
import com.yowyob.fleet.infrastructure.adapters.outbound.kernel.exception.KernelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebInputException;

import java.net.URI;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException ex) {
        log.warn("⚠️ Business Exception [{}] : {}", ex.getBusinessCode(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle("Business Error");
        problem.setProperty("code", ex.getBusinessCode());
        problem.setProperty("timestamp", ex.getTimestamp());
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("⛔ Access Denied : {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Vous n'avez pas les droits nécessaires pour effectuer cette action.");
        problem.setTitle("Access Denied");
        problem.setType(URI.create("https://traensys.com/errors/forbidden"));
        return problem;
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleValidationException(WebExchangeBindException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, details);
        problem.setTitle("Validation Failed");
        return problem;
    }

    /**
     * Gestion des paramètres de requête invalides (ex: page=-1, size=abc).
     * Retourne un 400 clair plutôt qu'un 500 générique.
     */
    @ExceptionHandler(ServerWebInputException.class)
    public ProblemDetail handleServerWebInputException(ServerWebInputException ex) {
        log.warn("📥 Invalid request input : {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Paramètre de requête invalide : " + ex.getReason()
        );
        problem.setTitle("Invalid Request Parameter");
        problem.setProperty("hint", "Vérifiez les paramètres page (≥0), size (1-200), sort.");
        return problem;
    }

    /**
     * Gestion des erreurs de conversion de type (ex: UUID malformé dans le path).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("🔢 Illegal argument : {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Valeur invalide fournie : " + ex.getMessage()
        );
        problem.setTitle("Invalid Argument");
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDbViolation(DataIntegrityViolationException ex) {
        log.error("🗄️ Database Integrity Error: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "Une valeur fournie ne respecte pas les contraintes du système (ex: Statut invalide, Doublon unique). Vérifiez vos Enums.");
        problem.setTitle("Data Integrity Violation");
        return problem;
    }

    @ExceptionHandler(KernelException.class)
    public ProblemDetail handleKernelException(KernelException ex) {
        log.error("❌ Kernel error [{}]: {}", ex.getKernelCode(), ex.getMessage());
        HttpStatus status = switch (ex.getKernelCode()) {
            case "TENANT_REQUEST_QUOTA_EXCEEDED",
                 "ORGANIZATION_SERVICE_QUOTA_EXCEEDED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "ORGANIZATION_SERVICE_NOT_SUBSCRIBED",
                 "CLIENT_APPLICATION_SERVICE_NOT_ALLOWED" -> HttpStatus.FORBIDDEN;
            case "ORGANIZATION_CONTEXT_REQUIRED" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_GATEWAY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle("Kernel Service Error");
        problem.setProperty("kernelCode", ex.getKernelCode());
        return problem;
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ProblemDetail handleWebClientException(WebClientResponseException ex) {
        String responseBody = ex.getResponseBodyAsString();
        log.error("❌ EXTERNAL API ERROR [{} {}] : {}", ex.getStatusCode(), ex.getStatusText(), responseBody);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
            "Le service distant a retourné une erreur : " + ex.getStatusCode());
        problem.setTitle("External Service Error");
        problem.setProperty("remoteStatus", ex.getStatusCode().value());
        problem.setProperty("remoteDetails", responseBody);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralException(Exception ex) {
        log.error("💥 Critical error caught : ", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "Une erreur technique imprévue est survenue sur le serveur.");
        problem.setTitle("Internal Server Error");
        return problem;
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ProblemDetail handleResponseStatusException(org.springframework.web.server.ResponseStatusException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        problem.setTitle("Protocol Error");
        return problem;
    }
}