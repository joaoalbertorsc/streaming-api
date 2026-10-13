package com.ebac.streaming.plan;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.exception.IllegalPlanMigrationException;
import com.ebac.streaming.profile.AgeCategory;
import com.ebac.streaming.profile.Profile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanMigrationValidatorTest {

    private final PlanMigrationValidator validator = new PlanMigrationValidator();

    private Account premiumAccount() {
        Account account = Account.register("user@example.com", "hash", "pin");
        account.changePlan(Plan.PREMIUM);
        return account;
    }

    private List<Profile> fourProfiles(Account account) {
        Profile owner = Profile.createOwner(account, "Owner");
        Profile p1 = Profile.create(account, "P1", AgeCategory.ADULT);
        Profile p2 = Profile.create(account, "P2", AgeCategory.TEEN);
        Profile p3 = Profile.create(account, "P3", AgeCategory.CHILD);
        return List.of(owner, p1, p2, p3);
    }

    @Test
    void rejectsSamePlanMigration() {
        Account account = premiumAccount();
        List<Profile> profiles = fourProfiles(account);

        assertThatThrownBy(() ->
                validator.validate(account.getPlan(), Plan.PREMIUM, List.of(), profiles))
                .isInstanceOf(IllegalPlanMigrationException.class);
    }

    @Test
    void upgradeWithEmptyListIsAccepted() {
        Account account = Account.register("user@example.com", "hash", "pin");
        account.changePlan(Plan.BASIC);
        List<Profile> profiles = List.of(Profile.createOwner(account, "Owner"));

        assertThatCode(() ->
                validator.validate(Plan.BASIC, Plan.PREMIUM, List.of(), profiles))
                .doesNotThrowAnyException();
    }

    @Test
    void upgradeRejectsNonEmptyList() {
        Account account = Account.register("user@example.com", "hash", "pin");
        account.changePlan(Plan.BASIC);
        Profile owner = Profile.createOwner(account, "Owner");
        List<Profile> profiles = List.of(owner);

        assertThatThrownBy(() ->
                validator.validate(Plan.BASIC, Plan.PREMIUM, List.of(owner.getId()), profiles))
                .isInstanceOf(IllegalPlanMigrationException.class);
    }

    @Test
    void downgradeWithExactRequiredCountIsAccepted() {
        Account account = premiumAccount();
        List<Profile> profiles = fourProfiles(account);
        // Premium(4) -> Standard(2): must remove exactly 2 non-owner profiles
        List<UUID> toRemove = List.of(profiles.get(1).getId(), profiles.get(2).getId());

        assertThatCode(() ->
                validator.validate(Plan.PREMIUM, Plan.STANDARD, toRemove, profiles))
                .doesNotThrowAnyException();
    }

    @Test
    void downgradeRejectsTooFewProfiles() {
        Account account = premiumAccount();
        List<Profile> profiles = fourProfiles(account);
        List<UUID> toRemove = List.of(profiles.get(1).getId()); // only 1, needs 2

        assertThatThrownBy(() ->
                validator.validate(Plan.PREMIUM, Plan.STANDARD, toRemove, profiles))
                .isInstanceOf(IllegalPlanMigrationException.class);
    }

    @Test
    void downgradeRejectsTooManyProfiles() {
        Account account = premiumAccount();
        List<Profile> profiles = fourProfiles(account);
        List<UUID> toRemove = List.of(
                profiles.get(1).getId(), profiles.get(2).getId(), profiles.get(3).getId());

        assertThatThrownBy(() ->
                validator.validate(Plan.PREMIUM, Plan.STANDARD, toRemove, profiles))
                .isInstanceOf(IllegalPlanMigrationException.class);
    }

    @Test
    void rejectsWhenOwnerIsIncluded() {
        Account account = premiumAccount();
        List<Profile> profiles = fourProfiles(account);
        Profile owner = profiles.get(0);
        List<UUID> toRemove = List.of(owner.getId(), profiles.get(1).getId());

        assertThatThrownBy(() ->
                validator.validate(Plan.PREMIUM, Plan.STANDARD, toRemove, profiles))
                .isInstanceOf(IllegalPlanMigrationException.class);
    }

    @Test
    void rejectsWhenIdDoesNotBelongToAccount() {
        Account account = premiumAccount();
        List<Profile> profiles = fourProfiles(account);
        List<UUID> toRemove = List.of(profiles.get(1).getId(), UUID.randomUUID());

        assertThatThrownBy(() ->
                validator.validate(Plan.PREMIUM, Plan.STANDARD, toRemove, profiles))
                .isInstanceOf(IllegalPlanMigrationException.class);
    }

    @Test
    void rejectsWhenIdIsAlreadyTrashed() {
        Account account = premiumAccount();
        List<Profile> profiles = fourProfiles(account);
        Profile trashed = profiles.get(3);
        trashed.moveToTrash();
        // The service only loads active profiles; the validator receives that active-only list.
        List<Profile> activeProfiles = List.of(profiles.get(0), profiles.get(1), profiles.get(2));
        List<UUID> toRemove = List.of(profiles.get(1).getId(), trashed.getId());

        assertThatThrownBy(() ->
                validator.validate(Plan.PREMIUM, Plan.STANDARD, toRemove, activeProfiles))
                .isInstanceOf(IllegalPlanMigrationException.class);
    }

    @Test
    void rejectsDuplicateIds() {
        Account account = premiumAccount();
        List<Profile> profiles = fourProfiles(account);
        UUID duplicateId = profiles.get(1).getId();
        List<UUID> toRemove = List.of(duplicateId, duplicateId);

        assertThatThrownBy(() ->
                validator.validate(Plan.PREMIUM, Plan.STANDARD, toRemove, profiles))
                .isInstanceOf(IllegalPlanMigrationException.class);
    }
}