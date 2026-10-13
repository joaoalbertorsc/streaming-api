package com.ebac.streaming.movie;

import com.ebac.streaming.exception.ContentNotAllowedException;
import com.ebac.streaming.exception.MovieNotFoundException;
import com.ebac.streaming.plan.Plan;
import com.ebac.streaming.profile.AgeCategory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class MovieService {

    private final MovieRepository movieRepository;
    private final ContentAccessPolicy contentAccessPolicy;

    public MovieService(MovieRepository movieRepository, ContentAccessPolicy contentAccessPolicy) {
        this.movieRepository = movieRepository;
        this.contentAccessPolicy = contentAccessPolicy;
    }

    public List<Movie> listAllowed(Plan accountPlan, AgeCategory ageCategory) {
        List<Plan> allowedPlans = Arrays.stream(Plan.values())
                .filter(accountPlan::allows)
                .toList();
        List<ContentRating> allowedRatings = Arrays.stream(ContentRating.values())
                .filter(ageCategory::allows)
                .toList();

        return movieRepository.findAllowedFor(allowedPlans, allowedRatings);
    }

    public Movie getAllowed(UUID movieId, Plan accountPlan, AgeCategory ageCategory) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException("Movie not found."));

        if (!contentAccessPolicy.isAllowed(accountPlan, ageCategory, movie)) {
            throw new ContentNotAllowedException(
                    "This content is not permitted for this profile's classification or plan.");
        }

        return movie;
    }
}