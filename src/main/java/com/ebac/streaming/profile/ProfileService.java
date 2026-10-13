package com.ebac.streaming.profile;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.exception.IllegalProfileOperationException;
import com.ebac.streaming.exception.ProfileNameConflictException;
import com.ebac.streaming.exception.ProfileNotFoundException;
import com.ebac.streaming.exception.ProfileQuotaExceededException;
import com.ebac.streaming.plan.ProfileQuotaPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final ProfileQuotaPolicy quotaPolicy;

    public ProfileService(ProfileRepository profileRepository, ProfileQuotaPolicy quotaPolicy) {
        this.profileRepository = profileRepository;
        this.quotaPolicy = quotaPolicy;
    }

    @Transactional
    public Profile create(Account account, String name, AgeCategory ageCategory) {
        if (!quotaPolicy.canCreateNewProfile(account)) {
            throw new ProfileQuotaExceededException(
                    "This account's plan does not allow any more active profiles.");
        }

        Profile profile = Profile.create(account, name, ageCategory);
        profileRepository.save(profile);
        return profile;
    }

    @Transactional
    public void delete(Account account, UUID profileId) {
        Profile profile = findOwned(account, profileId);
        profile.moveToTrash();
    }

    @Transactional
    public Profile restore(Account account, UUID profileId) {
        Profile profile = findOwned(account, profileId);

        if (!quotaPolicy.canCreateNewProfile(account)) {
            throw new ProfileQuotaExceededException(
                    "This account's plan does not allow any more active profiles.");
        }
        if (profileRepository.existsByAccountAndNameAndDeletedAtIsNull(account, profile.getName())) {
            throw new ProfileNameConflictException(
                    "An active profile with this name already exists.");
        }

        profile.restore();
        return profile;
    }

    public List<Profile> listActive(Account account) {
        return profileRepository.findByAccountAndDeletedAtIsNull(account);
    }

    public List<Profile> listTrashed(Account account) {
        return profileRepository.findByAccountAndDeletedAtIsNotNull(account);
    }

    private Profile findOwned(Account account, UUID profileId) {
        return profileRepository.findByIdAndAccount(profileId, account)
                .orElseThrow(() -> new ProfileNotFoundException("Profile not found."));
    }
}