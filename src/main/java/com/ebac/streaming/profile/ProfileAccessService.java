package com.ebac.streaming.profile;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.exception.ProfileNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProfileAccessService {

    private final ProfileRepository profileRepository;

    public ProfileAccessService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public Profile getActiveOwnedProfile(Account account, UUID profileId) {
        Profile profile = profileRepository.findByIdAndAccount(profileId, account)
                .orElseThrow(() -> new ProfileNotFoundException("Profile not found."));

        if (!profile.isActive()) {
            throw new ProfileNotFoundException("Profile not found.");
        }

        return profile;
    }
}