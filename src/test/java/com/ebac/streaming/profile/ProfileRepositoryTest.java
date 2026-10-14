package com.ebac.streaming.profile;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Runs against a real PostgreSQL (Testcontainers) instead of the H2 "test" profile, because the
 * partial unique index on profile names (V6, db/migration-postgres) is PostgreSQL-only.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class ProfileRepositoryTest extends PostgresTestSupport {

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

    // --- Queries behind the quota and the trash rules ---

    private Profile persistTrashedDaysAgo(Account account, String name, int daysAgo) {
        Profile profile = Profile.create(account, name, AgeCategory.CHILD);
        profile.moveToTrash();
        entityManager.persist(profile);
        entityManager.flush();
        backdateDeletedAt(profile, daysAgo);
        return profile;
    }

    // deletedAt is always "now" in the domain; tests move it into the past directly in the database.
    private void backdateDeletedAt(Profile profile, int daysAgo) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE profile SET deleted_at = :deletedAt WHERE id = :id")
                .setParameter("deletedAt", Instant.now().minus(daysAgo, ChronoUnit.DAYS))
                .setParameter("id", profile.getId())
                .executeUpdate();
        entityManager.clear();
    }

    @Test
    void countOfActiveProfilesIgnoresTrashedOnesAndOtherAccounts() {
        Account account = persistAccount();
        Account otherAccount = Account.register("other@example.com", "hash", "pin");
        entityManager.persist(otherAccount);
        entityManager.persist(Profile.createOwner(account, "Owner"));
        entityManager.persist(Profile.create(account, "Teen", AgeCategory.TEEN));
        Profile trashed = Profile.create(account, "Old", AgeCategory.CHILD);
        trashed.moveToTrash();
        entityManager.persist(trashed);
        entityManager.persist(Profile.createOwner(otherAccount, "Someone else"));
        entityManager.flush();

        assertThat(profileRepository.countByAccountAndDeletedAtIsNull(account)).isEqualTo(2);
    }

    @Test
    void findsOnlyProfilesTrashedBeforeTheCutoff() {
        Account account = persistAccount();
        Profile expired = persistTrashedDaysAgo(account, "Expired", 31);
        persistTrashedDaysAgo(account, "Recent", 5);
        entityManager.persist(Profile.create(entityManager.find(Account.class, account.getId()), "Active",
                AgeCategory.ADULT));
        entityManager.flush();

        List<Profile> result = profileRepository.findByDeletedAtIsNotNullAndDeletedAtBefore(
                Instant.now().minus(30, ChronoUnit.DAYS));

        assertThat(result).extracting(Profile::getId).containsExactly(expired.getId());
    }
}
