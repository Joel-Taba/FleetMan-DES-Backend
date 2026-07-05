package com.yowyob.fleet.domain.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;

public abstract class DomainException extends RuntimeException {
    private final HttpStatus status;
    private final String businessCode;
    private final Instant timestamp;

    public HttpStatus getStatus() { return status; }
    public String getBusinessCode() { return businessCode; }
    public Instant getTimestamp() { return timestamp; }

    protected DomainException(String message, HttpStatus status, String businessCode) {
        super(message);
        this.status = status;
        this.businessCode = businessCode;
        this.timestamp = Instant.now();
    }
}
