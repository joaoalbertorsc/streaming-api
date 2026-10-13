package com.ebac.streaming.plan;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.profile.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ProfileQuotaPolicyTest {

    @Mock
    private ProfileRepository profileRepository;

    private ProfileQuotaPolicy policy;
    private Account account;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        policy = new ProfileQuotaPolicy(profileRepository);
        account = Account.register("user@example.com", "hash", "pin");
        account.changePlan(Plan.STANDARD); // maxProfiles = 2
    }

    @Test
    void allowsCreationWhenUnderQuota() {
        when(profileRepository.countByAccountAndDeletedAtIsNull(any())).thenReturn(1L);

        assertThat(policy.canCreateNewProfile(account)).isTrue();
    }

    @Test
    void rejectsCreationWhenAtQuota() {
        when(profileRepository.countByAccountAndDeletedAtIsNull(any())).thenReturn(2L);

        assertThat(policy.canCreateNewProfile(account)).isFalse();
    }
}