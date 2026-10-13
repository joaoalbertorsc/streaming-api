package com.ebac.streaming.watch;

import java.time.Instant;
import java.util.UUID;

public record HistoryEntryResponse(UUID movieId, String movieTitle, Instant watchedAt) {

    public static HistoryEntryResponse from(WatchHistoryEntry entry) {
        return new HistoryEntryResponse(entry.getMovie().getId(), entry.getMovie().getTitle(),
                entry.getWatchedAt());
    }
}
