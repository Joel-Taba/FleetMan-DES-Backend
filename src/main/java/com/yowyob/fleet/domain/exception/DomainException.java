package com.yowyob.fleet.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.Instant;

@Getter
public abstract class DomainException extends RuntimeException {
    private final HttpStatus status;
    private final String businessCode;
    private final Instant timestamp;

    protected DomainException(String message, HttpStatus status, String businessCode) {
        super(message);
        this.status = status;
        this.businessCode = businessCode;
        this.timestamp = Instant.now();
    }
}