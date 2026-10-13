package com.ebac.streaming.review;

import com.ebac.streaming.exception.MovieNotWatchedException;
import com.ebac.streaming.exception.ReviewAlreadyExistsException;
import com.ebac.streaming.exception.ReviewNotFoundException;
import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.profile.Profile;
import com.ebac.streaming.watch.WatchHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final WatchHistoryRepository watchHistoryRepository;

    public ReviewService(ReviewRepository reviewRepository, WatchHistoryRepository watchHistoryRepository) {
        this.reviewRepository = reviewRepository;
        this.watchHistoryRepository = watchHistoryRepository;
    }

    @Transactional
    public Review create(Profile profile, Movie movie, int stars, String comment) {
        if (!watchHistoryRepository.existsByProfileAndMovie(profile, movie)) {
            throw new MovieNotWatchedException("This profile has not watched this movie yet.");
        }
        if (reviewRepository.findByProfileAndMovie(profile, movie).isPresent()) {
            throw new ReviewAlreadyExistsException(
                    "A review already exists for this movie. Use update instead.");
        }

        Review review = new Review(profile, movie, stars, comment);
        reviewRepository.save(review);
        return review;
    }

    @Transactional
    public Review update(Profile profile, UUID reviewId, int stars, String comment) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException("Review not found."));

        if (!review.getProfile().equals(profile)) {
            throw new ReviewNotFoundException("Review not found.");
        }

        review.update(stars, comment);
        return review;
    }

    public List<Review> list(Profile profile) {
        return reviewRepository.findByProfile(profile);
    }
}