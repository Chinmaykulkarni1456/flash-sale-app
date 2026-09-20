package com.flashsale.commons.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final String title;
    private final HttpStatus status;

    // 1-argument: message only
    public BusinessException(String message) {
        super(message);
        this.errorCode = "INTERNAL_ERROR";
        this.title = "Business Error";
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    // 2-argument: errorCode, message
    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.title = errorCode;
        this.status = HttpStatus.BAD_REQUEST;
    }

    // 3-argument: errorCode, message, status
    public BusinessException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.title = errorCode;
        this.status = status;
    }

    // 4-argument: errorCode, title, message, status
    public BusinessException(String errorCode, String title, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.title = title;
        this.status = status;
    }
}