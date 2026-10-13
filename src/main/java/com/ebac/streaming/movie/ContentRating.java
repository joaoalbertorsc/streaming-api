package com.ebac.streaming.movie;

public enum ContentRating {
    L(0),
    TEN(10),
    TWELVE(12),
    FOURTEEN(14),
    SIXTEEN(16),
    EIGHTEEN(18);

    private final int minimumAge;

    ContentRating(int minimumAge) {
        this.minimumAge = minimumAge;
    }

    public int minimumAge() {
        return minimumAge;
    }

    public boolean requiresPin() {
        return this == SIXTEEN || this == EIGHTEEN;
    }
}
