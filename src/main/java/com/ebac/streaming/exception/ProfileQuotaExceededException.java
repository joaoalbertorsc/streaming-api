package com.ebac.streaming.exception;

public class ProfileQuotaExceededException extends RuntimeException {

    public ProfileQuotaExceededException(String message) {
        super(message);
    }
}