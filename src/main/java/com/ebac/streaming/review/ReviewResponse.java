package com.ebac.streaming.review;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(UUID id, UUID movieId, int stars, String comment, Instant updatedAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getMovie().getId(), review.getStars(),
                review.getComment(), review.getUpdatedAt());
    }
}