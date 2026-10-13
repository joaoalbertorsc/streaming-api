package com.ebac.streaming.exception;

public class MovieNotWatchedException extends RuntimeException {

    public MovieNotWatchedException(String message) {
        super(message);
    }
}