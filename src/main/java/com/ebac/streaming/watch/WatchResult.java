package com.ebac.streaming.watch;

import java.time.Instant;

public record WatchResult(String movieTitle, String message, Instant watchedAt) {
}