package com.vietnam.pji.exception;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }

    /**
     * @param errorCode      legacy numeric code; the message carries the meaning
     * @param message        client-facing description of the failed operation
     * @param cause          the underlying storage/SDK failure
     */
    public BusinessException(String errorCode, String message, Exception cause) {
        super(message, cause);
    }
}
