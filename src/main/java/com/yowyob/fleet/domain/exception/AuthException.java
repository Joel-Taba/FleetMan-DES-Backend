package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

public class AuthException extends DomainException {

    public AuthException(String message, HttpStatus status, String code) {
        super(message, status, code);
    }

    public static AuthException invalidCredentials() {
        return new AuthException("Identifiants ou mot de passe incorrects.", HttpStatus.UNAUTHORIZED, "AUTH_001");
    }

    public static AuthException accountLocked() {
        return new AuthException("Votre compte est suspendu localement. Contactez un admin.", HttpStatus.FORBIDDEN, "AUTH_002");
    }

    public static AuthException accountDeleted() {
        return new AuthException("Ce compte n'existe plus dans le système.", HttpStatus.FORBIDDEN, "AUTH_003");
    }

    public static AuthException userAlreadyExists() {
        return new AuthException("Un utilisateur avec cet email ou ce nom d'utilisateur existe déjà.", HttpStatus.CONFLICT, "AUTH_004");
    }

    public static AuthException tokenExpired() {
        return new AuthException("Votre session a expiré. Veuillez vous reconnecter.", HttpStatus.UNAUTHORIZED, "AUTH_005");
    }

    public static AuthException remoteServiceUnavailable() {
        return new AuthException("Le service d'authentification central est indisponible.", HttpStatus.SERVICE_UNAVAILABLE, "AUTH_006");
    }
    
    public static AuthException generic(String message, HttpStatus status) {
        return new AuthException(message, status, "AUTH_GENERIC");
    }
}