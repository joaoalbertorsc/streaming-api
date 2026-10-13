package com.ebac.streaming.watch;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.exception.InvalidPinException;
import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.movie.MovieService;
import com.ebac.streaming.profile.Profile;
import com.ebac.streaming.profile.ProfileAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class WatchService {

    private final ProfileAccessService profileAccessService;
    private final MovieService movieService;
    private final PinPolicy pinPolicy;
    private final WatchHistoryRepository watchHistoryRepository;

    public WatchService(ProfileAccessService profileAccessService, MovieService movieService,
                        PinPolicy pinPolicy, WatchHistoryRepository watchHistoryRepository) {
        this.profileAccessService = profileAccessService;
        this.movieService = movieService;
        this.pinPolicy = pinPolicy;
        this.watchHistoryRepository = watchHistoryRepository;
    }

    @Transactional
    public WatchResult watch(Account account, UUID profileId, UUID movieId, String rawPin) {
        Profile profile = profileAccessService.getActiveOwnedProfile(account, profileId);

        Movie movie = movieService.getAllowed(movieId, account.getPlan(), profile.getAgeCategory());

        if (movie.getClassification().requiresPin()) {
            if (!StringUtils.hasText(rawPin)) {
                throw new InvalidPinException("A PIN is required to watch this content.");
            }
            pinPolicy.validate(rawPin, account.getPinHash());
        }

        WatchHistoryEntry entry = new WatchHistoryEntry(profile, movie);
        watchHistoryRepository.save(entry);

        System.out.println("Movie started: " + movie.getTitle());
        System.out.println("Movie running...");
        System.out.println("Movie ended.");

        return new WatchResult(movie.getTitle(), "Movie ended", entry.getWatchedAt());
    }
}