package com.ebac.streaming.movie;

import com.ebac.streaming.plan.Plan;
import com.ebac.streaming.profile.AgeCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentAccessPolicyTest {

    private final ContentAccessPolicy policy = new ContentAccessPolicy();

    private Movie movie(Plan minimumPlan, ContentRating classification) {
        return new Movie("Title", "Genre", "Synopsis", 2020, "Cast",
                100, classification, minimumPlan);
    }

    @Test
    void allowsWhenPlanAndAgeBothPass() {
        boolean result = policy.isAllowed(Plan.PREMIUM, AgeCategory.ADULT,
                movie(Plan.STANDARD, ContentRating.SIXTEEN));

        assertThat(result).isTrue();
    }

    @Test
    void rejectsWhenPlanFailsEvenIfAgePasses() {
        boolean result = policy.isAllowed(Plan.BASIC, AgeCategory.ADULT,
                movie(Plan.STANDARD, ContentRating.L));

        assertThat(result).isFalse();
    }

    @Test
    void rejectsWhenAgeFailsEvenIfPlanPasses() {
        boolean result = policy.isAllowed(Plan.PREMIUM, AgeCategory.CHILD,
                movie(Plan.BASIC, ContentRating.TEN));

        assertThat(result).isFalse();
    }

    @Test
    void rejectsWhenBothFail() {
        boolean result = policy.isAllowed(Plan.BASIC, AgeCategory.CHILD,
                movie(Plan.PREMIUM, ContentRating.EIGHTEEN));

        assertThat(result).isFalse();
    }
}