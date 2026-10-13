package com.ebac.streaming.movie;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.auth.CurrentAccountProvider;
import com.ebac.streaming.profile.Profile;
import com.ebac.streaming.profile.ProfileAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/movies")
public class MovieController {

    private final MovieService movieService;
    private final ProfileAccessService profileAccessService;
    private final CurrentAccountProvider currentAccountProvider;

    public MovieController(MovieService movieService, ProfileAccessService profileAccessService,
                           CurrentAccountProvider currentAccountProvider) {
        this.movieService = movieService;
        this.profileAccessService = profileAccessService;
        this.currentAccountProvider = currentAccountProvider;
    }

    @GetMapping
    public ResponseEntity<List<MovieSummaryResponse>> list(@RequestParam UUID profileId) {
        Account account = currentAccountProvider.getCurrentAccount();
        Profile profile = profileAccessService.getActiveOwnedProfile(account, profileId);

        List<MovieSummaryResponse> movies = movieService.listAllowed(account.getPlan(), profile.getAgeCategory())
                .stream()
                .map(MovieSummaryResponse::from)
                .toList();

        return ResponseEntity.ok(movies);
    }

    @GetMapping("/{movieId}")
    public ResponseEntity<MovieResponse> get(@PathVariable UUID movieId, @RequestParam UUID profileId) {
        Account account = currentAccountProvider.getCurrentAccount();
        Profile profile = profileAccessService.getActiveOwnedProfile(account, profileId);

        Movie movie = movieService.getAllowed(movieId, account.getPlan(), profile.getAgeCategory());

        return ResponseEntity.ok(MovieResponse.from(movie));
    }
}