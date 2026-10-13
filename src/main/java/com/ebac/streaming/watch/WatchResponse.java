package com.ebac.streaming.watch;

import java.time.Instant;

public record WatchResponse(String movieTitle, String message, Instant watchedAt) {

    public static WatchResponse from(WatchResult result) {
        return new WatchResponse(result.movieTitle(), result.message(), result.watchedAt());
    }
}