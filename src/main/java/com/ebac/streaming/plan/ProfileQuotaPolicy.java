package com.ebac.streaming.plan;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.profile.ProfileRepository;
import org.springframework.stereotype.Component;

@Component
public class ProfileQuotaPolicy {

    private final ProfileRepository profileRepository;

    public ProfileQuotaPolicy(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public boolean canCreateNewProfile(Account account) {
        long activeProfiles = profileRepository.countByAccountAndDeletedAtIsNull(account);
        return activeProfiles < account.getPlan().maxProfiles();
    }
}