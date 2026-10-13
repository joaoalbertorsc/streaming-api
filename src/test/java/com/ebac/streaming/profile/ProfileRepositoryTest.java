package com.ebac.streaming.profile;

import com.ebac.streaming.account.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Runs against a real PostgreSQL (Testcontainers) instead of the H2 "test" profile, because the
 * partial unique index on profile names (V6, db/migration-postgres) is PostgreSQL-only.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
class ProfileRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private TestEntityManager entityManager;
    @Autowired private ProfileRepository profileRepository;

    private Account persistAccount() {
        Account account = Account.register("user@example.com", "hash", "pin");
        entityManager.persist(account);
        return account;
    }

    @Test
    void allowsDuplicateNameBetweenActiveAndTrashedProfile() {
        Account account = persistAccount();
        Profile trashed = Profile.create(account, "Kids", AgeCategory.CHILD);
        trashed.moveToTrash();
        entityManager.persist(trashed);
        entityManager.flush();

        Profile active = Profile.create(account, "Kids", AgeCategory.CHILD);

        assertThatCode(() -> {
            entityManager.persist(active);
            entityManager.flush();
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateNameBetweenTwoActiveProfiles() {
        Account account = persistAccount();
        Profile first = Profile.create(account, "Kids", AgeCategory.CHILD);
        entityManager.persist(first);
        entityManager.flush();

        Profile second = Profile.create(account, "Kids", AgeCategory.TEEN);
        entityManager.persist(second);

        assertThatThrownBy(() -> entityManager.flush())
                .hasStackTraceContaining("uq_profile_account_name_active");
    }

    @Test
    void allowsSameNameOnDifferentAccounts() {
        Account firstAccount = persistAccount();
        Account secondAccount = Account.register("other@example.com", "hash", "pin");
        entityManager.persist(secondAccount);
        entityManager.persist(Profile.create(firstAccount, "Kids", AgeCategory.CHILD));
        entityManager.flush();

        Profile sameNameElsewhere = Profile.create(secondAccount, "Kids", AgeCategory.CHILD);

        assertThatCode(() -> {
            entityManager.persist(sameNameElsewhere);
            entityManager.flush();
        }).doesNotThrowAnyException();
    }
}
