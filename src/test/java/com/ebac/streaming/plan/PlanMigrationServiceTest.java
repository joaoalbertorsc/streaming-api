package com.ebac.streaming.plan;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.account.AccountRepository;
import com.ebac.streaming.exception.IllegalPlanMigrationException;
import com.ebac.streaming.profile.AgeCategory;
import com.ebac.streaming.profile.Profile;
import com.ebac.streaming.profile.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class PlanMigrationServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private PlanMigrationValidator validator;

    private PlanMigrationService service;
    private Account account;
    private Profile owner;
    private Profile p1;
    private Profile p2;
    private Profile p3;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PlanMigrationService(accountRepository, profileRepository, validator);

        account = Account.register("user@example.com", "hash", "pin");
        account.changePlan(Plan.PREMIUM);
        owner = Profile.createOwner(account, "Owner");
        p1 = Profile.create(account, "P1", AgeCategory.ADULT);
        p2 = Profile.create(account, "P2", AgeCategory.TEEN);
        p3 = Profile.create(account, "P3", AgeCategory.CHILD);

        when(profileRepository.findByAccountAndDeletedAtIsNull(account))
                .thenReturn(List.of(owner, p1, p2, p3));
    }

    @Test
    void validatorFailureLeavesNothingPersisted() {
        List<java.util.UUID> toRemove = List.of(p1.getId());
        doThrow(new IllegalPlanMigrationException("bad request"))
                .when(validator).validate(eq(Plan.PREMIUM), eq(Plan.STANDARD), eq(toRemove), anyList());

        assertThatThrownBy(() -> service.migrate(account, Plan.STANDARD, toRemove))
                .isInstanceOf(IllegalPlanMigrationException.class);

        verify(accountRepository, never()).save(any());
        verify(profileRepository, never()).saveAll(any());
        assertThat(account.getPlan()).isEqualTo(Plan.PREMIUM);
    }

    @Test
    void successfulDowngradeTrashesProfilesAndChangesPlan() {
        List<java.util.UUID> toRemove = List.of(p1.getId(), p2.getId());
        doNothing().when(validator)
                .validate(eq(Plan.PREMIUM), eq(Plan.STANDARD), eq(toRemove), anyList());

        service.migrate(account, Plan.STANDARD, toRemove);

        assertThat(account.getPlan()).isEqualTo(Plan.STANDARD);
        assertThat(p1.isActive()).isFalse();
        assertThat(p2.isActive()).isFalse();
        assertThat(p3.isActive()).isTrue();
        verify(accountRepository).save(account);
    }

    @Test
    void upgradeChangesPlanWithNoProfileSideEffects() {
        account.changePlan(Plan.BASIC);
        when(profileRepository.findByAccountAndDeletedAtIsNull(account))
                .thenReturn(List.of(owner));
        doNothing().when(validator)
                .validate(eq(Plan.BASIC), eq(Plan.PREMIUM), eq(List.of()), anyList());

        service.migrate(account, Plan.PREMIUM, List.of());

        assertThat(account.getPlan()).isEqualTo(Plan.PREMIUM);
        assertThat(owner.isActive()).isTrue();
        verify(accountRepository).save(account);
    }

    @Test
    void previewComputesRequiredRemovalsAndEligibleNonOwnerProfiles() {
        MigrationPreview preview = service.preview(account, Plan.STANDARD);

        assertThat(preview.currentPlan()).isEqualTo(Plan.PREMIUM);
        assertThat(preview.targetPlan()).isEqualTo(Plan.STANDARD);
        assertThat(preview.profilesToRemove()).isEqualTo(2); // 4 active - 2 (Standard max)
        assertThat(preview.eligibleProfiles()).containsExactly(p1, p2, p3); // owner excluded
    }

    @Test
    void previewForUpgradeRequiresNoRemovals() {
        account.changePlan(Plan.BASIC);
        when(profileRepository.findByAccountAndDeletedAtIsNull(account))
                .thenReturn(List.of(owner));

        MigrationPreview preview = service.preview(account, Plan.PREMIUM);

        assertThat(preview.profilesToRemove()).isEqualTo(0);
        assertThat(preview.eligibleProfiles()).isEmpty();
    }
}