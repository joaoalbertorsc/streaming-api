package com.ebac.streaming.watch;

import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.profile.Profile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "watch_history_entry")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchHistoryEntry {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "watched_at", nullable = false)
    private Instant watchedAt;

    public WatchHistoryEntry(Profile profile, Movie movie) {
        this.id = UUID.randomUUID();
        this.profile = profile;
        this.movie = movie;
        this.watchedAt = Instant.now();
    }
}