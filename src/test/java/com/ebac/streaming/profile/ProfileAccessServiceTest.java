package com.ebac.streaming.profile;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.exception.ProfileNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class ProfileAccessServiceTest {

    @Mock private ProfileRepository profileRepository;

    private ProfileAccessService accessService;
    private Account account;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        accessService = new ProfileAccessService(profileRepository);
        account = Account.register("user@example.com", "hash", "pin");
    }

    @Test
    void returnsProfileWhenActiveAndOwnedByAccount() {
        Profile profile = Profile.create(account, "Kids", AgeCategory.CHILD);
        when(profileRepository.findByIdAndAccount(profile.getId(), account))
                .thenReturn(Optional.of(profile));

        Profile result = accessService.getActiveOwnedProfile(account, profile.getId());

        assertThat(result).isEqualTo(profile);
    }

    @Test
    void throwsWhenProfileDoesNotBelongToAccount() {
        UUID randomId = UUID.randomUUID();
        when(profileRepository.findByIdAndAccount(randomId, account)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.getActiveOwnedProfile(account, randomId))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void throwsWhenProfileIsTrashed() {
        Profile profile = Profile.create(account, "Kids", AgeCategory.CHILD);
        profile.moveToTrash();
        when(profileRepository.findByIdAndAccount(profile.getId(), account))
                .thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> accessService.getActiveOwnedProfile(account, profile.getId()))
                .isInstanceOf(ProfileNotFoundException.class);
    }
}