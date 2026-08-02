package com.vietnam.pji.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GONE)
public class UploadSessionGoneException extends RuntimeException {
    public UploadSessionGoneException(String message) {
        super(message);
    }
}
