package com.ebac.streaming.movie;

import com.ebac.streaming.exception.ContentNotAllowedException;
import com.ebac.streaming.exception.MovieNotFoundException;
import com.ebac.streaming.plan.Plan;
import com.ebac.streaming.profile.AgeCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

class MovieServiceTest {

    @Mock private MovieRepository movieRepository;
    private final ContentAccessPolicy contentAccessPolicy = new ContentAccessPolicy();

    private MovieService movieService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        movieService = new MovieService(movieRepository, contentAccessPolicy);
    }

    private Movie movie(Plan minimumPlan, ContentRating classification) {
        return new Movie("Title", "Genre", "Synopsis", 2020, "Cast", 100,
                classification, minimumPlan);
    }

    @Test
    void listAllowedReturnsRepositoryResultForPlanAndAge() {
        Movie allowedMovie = movie(Plan.BASIC, ContentRating.TEN);
        when(movieRepository.findAllowedFor(anyList(), anyList()))
                .thenReturn(List.of(allowedMovie));

        List<Movie> result = movieService.listAllowed(Plan.STANDARD, AgeCategory.TEEN);

        assertThat(result).containsExactly(allowedMovie);
    }

    @Test
    void getAllowedReturnsMovieWhenAccessIsAllowed() {
        Movie allowedMovie = movie(Plan.BASIC, ContentRating.L);
        when(movieRepository.findById(allowedMovie.getId())).thenReturn(Optional.of(allowedMovie));

        Movie result = movieService.getAllowed(allowedMovie.getId(), Plan.PREMIUM, AgeCategory.ADULT);

        assertThat(result).isEqualTo(allowedMovie);
    }

    @Test
    void getAllowedThrowsWhenAccessIsBlocked() {
        Movie adultMovie = movie(Plan.PREMIUM, ContentRating.EIGHTEEN);
        when(movieRepository.findById(adultMovie.getId())).thenReturn(Optional.of(adultMovie));

        assertThatThrownBy(() ->
                movieService.getAllowed(adultMovie.getId(), Plan.BASIC, AgeCategory.CHILD))
                .isInstanceOf(ContentNotAllowedException.class);
    }

    @Test
    void getAllowedThrowsWhenMovieNotFound() {
        UUID randomId = UUID.randomUUID();
        when(movieRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                movieService.getAllowed(randomId, Plan.PREMIUM, AgeCategory.ADULT))
                .isInstanceOf(MovieNotFoundException.class);
    }
}