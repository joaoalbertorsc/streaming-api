package com.ebac.streaming.account;

import com.ebac.streaming.plan.Plan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTest {

    @Test
    void registerDefaultsToBasicPlan() {
        Account account = Account.register("user@example.com", "hashedPassword", "hashedPin");

        assertThat(account.getPlan()).isEqualTo(Plan.BASIC);
    }

    @Test
    void registerStoresEmailAndHashedCredentials() {
        Account account = Account.register("user@example.com", "hashedPassword", "hashedPin");

        assertThat(account.getEmail()).isEqualTo("user@example.com");
        assertThat(account.getPasswordHash()).isEqualTo("hashedPassword");
        assertThat(account.getPinHash()).isEqualTo("hashedPin");
    }

    @Test
    void changePlanUpdatesPlan() {
        Account account = Account.register("user@example.com", "hashedPassword", "hashedPin");

        account.changePlan(Plan.PREMIUM);

        assertThat(account.getPlan()).isEqualTo(Plan.PREMIUM);
    }
}