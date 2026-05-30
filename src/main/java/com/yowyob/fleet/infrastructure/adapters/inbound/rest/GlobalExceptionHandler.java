package com.yowyob.fleet.infrastructure.adapters.inbound.rest;

import com.yowyob.fleet.domain.exception.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException; // Import nécessaire
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException; // Import nécessaire
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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

    // --- CORRECTION 1 : GESTION SÉCURITÉ ---
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("⛔ Access Denied : {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Vous n'avez pas les droits nécessaires pour effectuer cette action.");
        problem.setTitle("Access Denied");
        problem.setType(URI.create("https://traensys.com/errors/forbidden"));
        return problem;
    }

    // --- CORRECTION 2 : ERREURS VALIDATION (inchangé) ---
    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleValidationException(WebExchangeBindException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, details);
        problem.setTitle("Validation Failed");
        return problem;
    }

    // --- CORRECTION 3 : CONTRAINTES DB (ENUMS) ---
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDbViolation(DataIntegrityViolationException ex) {
        log.error("🗄️ Database Integrity Error: {}", ex.getMessage());
        // On masque le message SQL brut pour la sécurité, on donne un indice générique
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, 
            "Une valeur fournie ne respecte pas les contraintes du système (ex: Statut invalide, Doublon unique). Vérifiez vos Enums.");
        problem.setTitle("Data Integrity Violation");
        return problem;
    }

    // --- GESTION ERREURS EXTERNES ---
    @ExceptionHandler(WebClientResponseException.class)
    public ProblemDetail handleWebClientException(WebClientResponseException ex) {
        String responseBody = ex.getResponseBodyAsString();
        log.error("❌ EXTERNAL API ERROR [{} {}] : {}", ex.getStatusCode(), ex.getStatusText(), responseBody);
        
        // On renvoie 502 (Bad Gateway) pour bien signifier que c'est le service tiers qui a échoué
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, 
            "Le service distant a retourné une erreur : " + ex.getStatusCode());
        problem.setTitle("External Service Error");
        problem.setProperty("remoteStatus", ex.getStatusCode().value());
        // On inclut le corps de l'erreur distante pour aider le debug frontend
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