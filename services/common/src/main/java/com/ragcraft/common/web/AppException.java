package com.ragcraft.common.web;

import org.springframework.http.HttpStatus;

/** Business error with an HTTP status. Serialised as {"detail": message}, matching the FastAPI contract. */
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AppException(HttpStatus status, String message) {
        this(status, message, null);
    }

    /** `code` lets the frontend branch on a condition (for example LOCKED or MFA_REQUIRED) without parsing the message. */
    public AppException(HttpStatus status, String message, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static AppException notFound(String message) { return new AppException(HttpStatus.NOT_FOUND, message); }
    public static AppException badRequest(String message) { return new AppException(HttpStatus.BAD_REQUEST, message); }
    public static AppException forbidden(String message) { return new AppException(HttpStatus.FORBIDDEN, message); }
    public static AppException conflict(String message) { return new AppException(HttpStatus.CONFLICT, message); }
    public static AppException unauthorized(String message) { return new AppException(HttpStatus.UNAUTHORIZED, message); }
    public static AppException withCode(HttpStatus status, String message, String code) { return new AppException(status, message, code); }
}
