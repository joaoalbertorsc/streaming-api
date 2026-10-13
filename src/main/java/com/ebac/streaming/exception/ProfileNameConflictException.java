package com.ebac.streaming.exception;

public class ProfileNameConflictException extends RuntimeException {

    public ProfileNameConflictException(String message) {
        super(message);
    }
}