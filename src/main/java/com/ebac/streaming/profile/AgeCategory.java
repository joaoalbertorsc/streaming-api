package com.ebac.streaming.profile;

import com.ebac.streaming.movie.ContentRating;

public enum AgeCategory {
    CHILD(0),
    TEEN(14),
    ADULT(18);

    private final int maxAllowedRatingAge;

    AgeCategory(int maxAllowedRatingAge) {
        this.maxAllowedRatingAge = maxAllowedRatingAge;
    }

    public boolean allows(ContentRating rating) {
        return rating.minimumAge() <= this.maxAllowedRatingAge;
    }
}
