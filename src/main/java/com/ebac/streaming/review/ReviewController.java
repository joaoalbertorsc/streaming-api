package com.ebac.streaming.review;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.auth.CurrentAccountProvider;
import com.ebac.streaming.exception.MovieNotFoundException;
import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.movie.MovieRepository;
import com.ebac.streaming.profile.Profile;
import com.ebac.streaming.profile.ProfileAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final ProfileAccessService profileAccessService;
    private final MovieRepository movieRepository;
    private final CurrentAccountProvider currentAccountProvider;

    public ReviewController(ReviewService reviewService, ProfileAccessService profileAccessService,
                            MovieRepository movieRepository, CurrentAccountProvider currentAccountProvider) {
        this.reviewService = reviewService;
        this.profileAccessService = profileAccessService;
        this.movieRepository = movieRepository;
        this.currentAccountProvider = currentAccountProvider;
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> create(@RequestParam UUID profileId,
                                                 @Valid @RequestBody ReviewRequest request) {
        Account account = currentAccountProvider.getCurrentAccount();
        Profile profile = profileAccessService.getActiveOwnedProfile(account, profileId);
        Movie movie = findMovie(request.movieId());

        Review review = reviewService.create(profile, movie, request.stars(), request.comment());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewResponse.from(review));
    }

    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> update(@PathVariable UUID reviewId, @RequestParam UUID profileId,
                                                 @Valid @RequestBody ReviewUpdateRequest request) {
        Account account = currentAccountProvider.getCurrentAccount();
        Profile profile = profileAccessService.getActiveOwnedProfile(account, profileId);

        Review review = reviewService.update(profile, reviewId, request.stars(), request.comment());
        return ResponseEntity.ok(ReviewResponse.from(review));
    }

    @GetMapping
    public ResponseEntity<List<ReviewResponse>> list(@RequestParam UUID profileId) {
        Account account = currentAccountProvider.getCurrentAccount();
        Profile profile = profileAccessService.getActiveOwnedProfile(account, profileId);

        List<ReviewResponse> reviews = reviewService.list(profile).stream()
                .map(ReviewResponse::from)
                .toList();
        return ResponseEntity.ok(reviews);
    }

    private Movie findMovie(UUID movieId) {
        return movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException("Movie not found."));
    }
}