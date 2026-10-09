package com.ragcraft.common.web;

import org.springframework.http.HttpStatus;

/** Raised when another service answers with an error; the status is passed through to the caller. */
public class ServiceCallException extends RuntimeException {

    private final HttpStatus status;

    public ServiceCallException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
