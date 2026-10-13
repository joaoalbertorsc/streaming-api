package com.ebac.streaming.profile;

import com.ebac.streaming.movie.ContentRating;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgeCategoryTest {

    @Test
    void childAllowsOnlyL() {
        assertThat(AgeCategory.CHILD.allows(ContentRating.L)).isTrue();
        assertThat(AgeCategory.CHILD.allows(ContentRating.TEN)).isFalse();
    }

    @Test
    void teenAllowsUpToFourteen() {
        assertThat(AgeCategory.TEEN.allows(ContentRating.FOURTEEN)).isTrue();
        assertThat(AgeCategory.TEEN.allows(ContentRating.SIXTEEN)).isFalse();
    }

    @Test
    void adultAllowsEverything() {
        assertThat(AgeCategory.ADULT.allows(ContentRating.EIGHTEEN)).isTrue();
        assertThat(AgeCategory.ADULT.allows(ContentRating.L)).isTrue();
    }
}
