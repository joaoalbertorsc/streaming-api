package com.ebac.streaming.review;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.movie.ContentRating;
import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.plan.Plan;
import com.ebac.streaming.profile.Profile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Bean Validation guards the HTTP boundary; these tests guard the entity itself, which any
// internal caller could otherwise put into an invalid state.
class ReviewTest {

    private Profile profile;
    private Movie movie;

    @BeforeEach
    void setUp() {
        Account account = Account.register("user@example.com", "hash", "pin");
        profile = Profile.createOwner(account, "Main");
        movie = new Movie("Title", "Genre", "Synopsis", 2020, "Cast", 100, ContentRating.L, Plan.BASIC);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1})
    void creationRejectsStarsOutsideOneToFive(int stars) {
        assertThatThrownBy(() -> new Review(profile, movie, stars, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void creationAcceptsTheBoundaryValues(int stars) {
        assertThat(new Review(profile, movie, stars, null).getStars()).isEqualTo(stars);
    }

    @Test
    void updateRejectsStarsOutsideOneToFiveAndKeepsThePreviousValues() {
        Review review = new Review(profile, movie, 3, "Ok");

        assertThatThrownBy(() -> review.update(6, "Changed"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(review.getStars()).isEqualTo(3);
        assertThat(review.getComment()).isEqualTo("Ok");
    }

    @Test
    void updateChangesStarsCommentAndUpdatedAt() throws InterruptedException {
        Review review = new Review(profile, movie, 3, "Ok");
        var updatedAtBefore = review.getUpdatedAt();
        Thread.sleep(5);

        review.update(5, "Better on rewatch");

        assertThat(review.getStars()).isEqualTo(5);
        assertThat(review.getComment()).isEqualTo("Better on rewatch");
        assertThat(review.getUpdatedAt()).isAfter(updatedAtBefore);
        assertThat(review.getCreatedAt()).isBeforeOrEqualTo(updatedAtBefore);
    }
}
