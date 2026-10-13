package com.ebac.streaming.movie;

import java.util.UUID;

public record MovieSummaryResponse(UUID id, String title, String genre, ContentRating classification) {

    public static MovieSummaryResponse from(Movie movie) {
        return new MovieSummaryResponse(movie.getId(), movie.getTitle(), movie.getGenre(),
                movie.getClassification());
    }
}