package com.ebac.streaming.review;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.exception.MovieNotWatchedException;
import com.ebac.streaming.exception.ReviewAlreadyExistsException;
import com.ebac.streaming.exception.ReviewNotFoundException;
import com.ebac.streaming.movie.ContentRating;
import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.plan.Plan;
import com.ebac.streaming.profile.AgeCategory;
import com.ebac.streaming.profile.Profile;
import com.ebac.streaming.watch.WatchHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private WatchHistoryRepository watchHistoryRepository;

    private ReviewService reviewService;
    private Account account;
    private Profile profile;
    private Movie movie;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        reviewService = new ReviewService(reviewRepository, watchHistoryRepository);
        account = Account.register("user@example.com", "hash", "pin");
        profile = Profile.create(account, "Main", AgeCategory.ADULT);
        movie = new Movie("Title", "Genre", "Synopsis", 2020, "Cast", 100,
                ContentRating.L, Plan.BASIC);
    }

    @Test
    void createRejectsWhenProfileNeverWatchedTheMovie() {
        when(watchHistoryRepository.existsByProfileAndMovie(profile, movie)).thenReturn(false);

        assertThatThrownBy(() -> reviewService.create(profile, movie, 5, "Great!"))
                .isInstanceOf(MovieNotWatchedException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createRejectsWhenReviewAlreadyExistsForThisProfileAndMovie() {
        when(watchHistoryRepository.existsByProfileAndMovie(profile, movie)).thenReturn(true);
        when(reviewRepository.findByProfileAndMovie(profile, movie))
                .thenReturn(Optional.of(new Review(profile, movie, 4, "Good")));

        assertThatThrownBy(() -> reviewService.create(profile, movie, 5, "Great!"))
                .isInstanceOf(ReviewAlreadyExistsException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    void createSavesReviewWhenWatchedAndNotYetReviewed() {
        when(watchHistoryRepository.existsByProfileAndMovie(profile, movie)).thenReturn(true);
        when(reviewRepository.findByProfileAndMovie(profile, movie)).thenReturn(Optional.empty());

        Review created = reviewService.create(profile, movie, 5, "Great!");

        assertThat(created.getStars()).isEqualTo(5);
        verify(reviewRepository).save(created);
    }

    @Test
    void updateRejectsWhenReviewDoesNotBelongToTheRequestingProfile() {
        Profile otherProfile = Profile.create(account, "Other", AgeCategory.ADULT);
        Review review = new Review(otherProfile, movie, 3, "Ok");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.update(profile, review.getId(), 5, "Changed"))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    @Test
    void updateThrowsWhenReviewDoesNotExist() {
        UUID randomId = UUID.randomUUID();
        when(reviewRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.update(profile, randomId, 5, "Changed"))
                .isInstanceOf(ReviewNotFoundException.class);
    }

    @Test
    void updateModifiesStarsAndComment() {
        Review review = new Review(profile, movie, 3, "Ok");
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        Review updated = reviewService.update(profile, review.getId(), 5, "Now great!");

        assertThat(updated.getStars()).isEqualTo(5);
        assertThat(updated.getComment()).isEqualTo("Now great!");
    }

    @Test
    void listReturnsProfilesOwnReviews() {
        when(reviewRepository.findByProfile(profile))
                .thenReturn(java.util.List.of(new Review(profile, movie, 4, "Nice")));

        assertThat(reviewService.list(profile)).hasSize(1);
    }
}