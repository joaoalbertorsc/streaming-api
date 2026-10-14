package com.ebac.streaming.profile;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.exception.IllegalProfileOperationException;
import com.ebac.streaming.exception.ProfileNameConflictException;
import com.ebac.streaming.exception.ProfileNotFoundException;
import com.ebac.streaming.exception.ProfileQuotaExceededException;
import com.ebac.streaming.plan.Plan;
import com.ebac.streaming.plan.ProfileQuotaPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProfileServiceTest {

    @Mock private ProfileRepository profileRepository;
    @Mock private ProfileQuotaPolicy quotaPolicy;

    private ProfileService profileService;
    private Account account;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        profileService = new ProfileService(profileRepository, quotaPolicy);
        account = Account.register("user@example.com", "hash", "pin");
        account.changePlan(Plan.STANDARD);
    }

    @Test
    void createDelegatesToQuotaPolicyAndSavesWhenAllowed() {
        when(quotaPolicy.canCreateNewProfile(account)).thenReturn(true);

        Profile created = profileService.create(account, "Kids", AgeCategory.CHILD);

        assertThat(created.getName()).isEqualTo("Kids");
        verify(profileRepository).save(created);
    }

    @Test
    void createRejectsWhenQuotaPolicyDenies() {
        when(quotaPolicy.canCreateNewProfile(account)).thenReturn(false);

        assertThatThrownBy(() -> profileService.create(account, "Kids", AgeCategory.CHILD))
                .isInstanceOf(ProfileQuotaExceededException.class);

        verify(profileRepository, never()).save(any());
    }

    @Test
    void deleteRejectsForOwnerProfile() {
        Profile owner = Profile.createOwner(account, "Main");
        when(profileRepository.findByIdAndAccount(owner.getId(), account))
                .thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> profileService.delete(account, owner.getId()))
                .isInstanceOf(IllegalProfileOperationException.class);
    }

    @Test
    void deleteSetsDeletedAtForNonOwnerProfile() {
        Profile profile = Profile.create(account, "Kids", AgeCategory.CHILD);
        when(profileRepository.findByIdAndAccount(profile.getId(), account))
                .thenReturn(Optional.of(profile));

        profileService.delete(account, profile.getId());

        assertThat(profile.isActive()).isFalse();
    }

    @Test
    void deleteThrowsWhenProfileNotFound() {
        UUID randomId = UUID.randomUUID();
        when(profileRepository.findByIdAndAccount(randomId, account)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.delete(account, randomId))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void restoreRejectsWhenQuotaIsFull() {
        Profile trashedProfile = Profile.create(account, "Kids", AgeCategory.CHILD);
        trashedProfile.moveToTrash();
        when(profileRepository.findByIdAndAccount(trashedProfile.getId(), account))
                .thenReturn(Optional.of(trashedProfile));
        when(quotaPolicy.canCreateNewProfile(account)).thenReturn(false);

        assertThatThrownBy(() -> profileService.restore(account, trashedProfile.getId()))
                .isInstanceOf(ProfileQuotaExceededException.class);
    }

    @Test
    void restoreRejectsOnNameConflictWithActiveProfile() {
        Profile trashedProfile = Profile.create(account, "Kids", AgeCategory.CHILD);
        trashedProfile.moveToTrash();
        when(profileRepository.findByIdAndAccount(trashedProfile.getId(), account))
                .thenReturn(Optional.of(trashedProfile));
        when(quotaPolicy.canCreateNewProfile(account)).thenReturn(true);
        when(profileRepository.existsByAccountAndNameAndDeletedAtIsNull(account, "Kids"))
                .thenReturn(true);

        assertThatThrownBy(() -> profileService.restore(account, trashedProfile.getId()))
                .isInstanceOf(ProfileNameConflictException.class);
    }

    @Test
    void restoreClearsDeletedAtWhenAllowed() {
        Profile trashedProfile = Profile.create(account, "Kids", AgeCategory.CHILD);
        trashedProfile.moveToTrash();
        when(profileRepository.findByIdAndAccount(trashedProfile.getId(), account))
                .thenReturn(Optional.of(trashedProfile));
        when(quotaPolicy.canCreateNewProfile(account)).thenReturn(true);
        when(profileRepository.existsByAccountAndNameAndDeletedAtIsNull(account, "Kids"))
                .thenReturn(false);

        profileService.restore(account, trashedProfile.getId());

        assertThat(trashedProfile.isActive()).isTrue();
    }

    @Test
    void listReturnsOnlyActiveProfiles() {
        Profile owner = Profile.createOwner(account, "Main");
        when(profileRepository.findByAccountAndDeletedAtIsNull(account))
                .thenReturn(List.of(owner));

        List<Profile> result = profileService.listActive(account);

        assertThat(result).containsExactly(owner);
    }

    @Test
    void listTrashedOnlyAsksForProfilesStillWithinTheRetentionWindow() {
        Profile trashed = Profile.create(account, "Kids", AgeCategory.CHILD);
        trashed.moveToTrash();
        when(profileRepository.findByAccountAndDeletedAtAfter(eq(account), any(Instant.class)))
                .thenReturn(List.of(trashed));

        List<Profile> result = profileService.listTrashed(account);

        assertThat(result).containsExactly(trashed);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(profileRepository).findByAccountAndDeletedAtAfter(eq(account), cutoff.capture());
        assertThat(cutoff.getValue())
                .isCloseTo(Instant.now().minus(Profile.TRASH_RETENTION), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void restoreTreatsAProfileTrashedLongerThanTheRetentionPeriodAsGone() {
        Profile expired = Profile.create(account, "Kids", AgeCategory.CHILD);
        expired.moveToTrash();
        ReflectionTestUtils.setField(expired, "deletedAt", Instant.now().minus(31, ChronoUnit.DAYS));
        when(profileRepository.findByIdAndAccount(expired.getId(), account)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> profileService.restore(account, expired.getId()))
                .isInstanceOf(ProfileNotFoundException.class);
        assertThat(expired.isActive()).isFalse();
        verifyNoInteractions(quotaPolicy);
    }
}