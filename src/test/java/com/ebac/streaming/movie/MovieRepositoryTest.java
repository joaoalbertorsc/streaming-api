package com.ebac.streaming.movie;

import com.ebac.streaming.plan.Plan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@ActiveProfiles("test")
class MovieRepositoryTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private MovieRepository movieRepository;

    private Movie persistMovie(Plan minimumPlan, ContentRating classification) {
        Movie movie = new Movie("Title", "Genre", "Synopsis", 2020, "Cast", 100,
                classification, minimumPlan);
        entityManager.persist(movie);
        return movie;
    }

    // The V7 seed migration already loads catalog movies, so these tests assert on the
    // filter itself (every result matches, our fixtures are in/out) rather than exact counts.

    @Test
    void returnsOnlyMoviesMatchingAllowedPlansAndRatings() {
        Movie basicAndL = persistMovie(Plan.BASIC, ContentRating.L);
        Movie standardAndTen = persistMovie(Plan.STANDARD, ContentRating.TEN);
        Movie premiumAndEighteen = persistMovie(Plan.PREMIUM, ContentRating.EIGHTEEN);
        entityManager.flush();

        List<Plan> allowedPlans = List.of(Plan.BASIC, Plan.STANDARD);
        List<ContentRating> allowedRatings = List.of(ContentRating.L, ContentRating.TEN);
        List<Movie> result = movieRepository.findAllowedFor(allowedPlans, allowedRatings);

        assertThat(result)
                .contains(basicAndL, standardAndTen)
                .doesNotContain(premiumAndEighteen)
                .allSatisfy(movie -> {
                    assertThat(allowedPlans).contains(movie.getMinimumPlan());
                    assertThat(allowedRatings).contains(movie.getClassification());
                });
    }

    @Test
    void excludesMoviesWhoseRatingIsNotAllowed() {
        Movie basicAndEighteen = persistMovie(Plan.BASIC, ContentRating.EIGHTEEN);
        entityManager.flush();

        List<Movie> result = movieRepository.findAllowedFor(
                List.of(Plan.BASIC, Plan.STANDARD, Plan.PREMIUM),
                List.of(ContentRating.L));

        assertThat(result)
                .doesNotContain(basicAndEighteen)
                .allSatisfy(movie -> assertThat(movie.getClassification()).isEqualTo(ContentRating.L));
    }
}