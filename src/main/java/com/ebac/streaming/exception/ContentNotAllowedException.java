package com.ebac.streaming.exception;

public class ContentNotAllowedException extends RuntimeException {

    public ContentNotAllowedException(String message) {
        super(message);
    }
}