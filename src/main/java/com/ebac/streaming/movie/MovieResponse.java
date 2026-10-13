package com.ebac.streaming.movie;

import com.ebac.streaming.plan.Plan;

import java.util.UUID;

public record MovieResponse(UUID id, String title, String genre, String synopsis, Integer releaseYear,
                            String castMembers, Integer durationMinutes, ContentRating classification,
                            Plan minimumPlan) {

    public static MovieResponse from(Movie movie) {
        return new MovieResponse(movie.getId(), movie.getTitle(), movie.getGenre(), movie.getSynopsis(),
                movie.getReleaseYear(), movie.getCastMembers(), movie.getDurationMinutes(),
                movie.getClassification(), movie.getMinimumPlan());
    }
}