package com.ebac.streaming.watch;

import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.profile.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WatchHistoryRepository extends JpaRepository<WatchHistoryEntry, UUID> {

    List<WatchHistoryEntry> findByProfileOrderByWatchedAtDesc(Profile profile);

    boolean existsByProfileAndMovie(Profile profile, Movie movie);

    void deleteByProfile(Profile profile);
}