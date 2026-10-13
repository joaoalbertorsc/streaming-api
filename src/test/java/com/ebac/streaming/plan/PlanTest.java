package com.ebac.streaming.plan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest {

    @Test
    void basicAllowsOneProfile() {
        assertThat(Plan.BASIC.maxProfiles()).isEqualTo(1);
    }

    @Test
    void standardAllowsTwoProfiles() {
        assertThat(Plan.STANDARD.maxProfiles()).isEqualTo(2);
    }

    @Test
    void premiumAllowsFourProfiles() {
        assertThat(Plan.PREMIUM.maxProfiles()).isEqualTo(4);
    }

    @Test
    void basicAllowsBasicContent() {
        assertThat(Plan.BASIC.allows(Plan.BASIC)).isTrue();
    }

    @Test
    void basicDoesNotAllowStandardContent() {
        assertThat(Plan.BASIC.allows(Plan.STANDARD)).isFalse();
    }

    @Test
    void premiumAllowsBasicContent() {
        assertThat(Plan.PREMIUM.allows(Plan.BASIC)).isTrue();
    }

    @Test
    void premiumAllowsPremiumContent() {
        assertThat(Plan.PREMIUM.allows(Plan.PREMIUM)).isTrue();
    }
}
