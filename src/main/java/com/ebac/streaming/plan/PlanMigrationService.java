package com.ebac.streaming.plan;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.account.AccountRepository;
import com.ebac.streaming.profile.Profile;
import com.ebac.streaming.profile.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlanMigrationService {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final PlanMigrationValidator validator;

    public PlanMigrationService(AccountRepository accountRepository, ProfileRepository profileRepository,
                                PlanMigrationValidator validator) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.validator = validator;
    }

    public MigrationPreview preview(Account account, Plan targetPlan) {
        List<Profile> activeProfiles = profileRepository.findByAccountAndDeletedAtIsNull(account);

        int requiredRemovals = Math.max(0, activeProfiles.size() - targetPlan.maxProfiles());

        List<Profile> eligibleProfiles = activeProfiles.stream()
                .filter(profile -> !profile.isOwner())
                .toList();

        return new MigrationPreview(account.getPlan(), targetPlan, requiredRemovals, eligibleProfiles);
    }

    @Transactional
    public void migrate(Account account, Plan targetPlan, List<UUID> profileIdsToRemove) {
        List<Profile> activeProfiles = profileRepository.findByAccountAndDeletedAtIsNull(account);

        validator.validate(account.getPlan(), targetPlan, profileIdsToRemove, activeProfiles);

        if (!profileIdsToRemove.isEmpty()) {
            Set<UUID> idsToRemove = Set.copyOf(profileIdsToRemove);
            Map<UUID, Profile> activeById = activeProfiles.stream()
                    .collect(Collectors.toMap(Profile::getId, p -> p));

            List<Profile> profilesToTrash = idsToRemove.stream()
                    .map(activeById::get)
                    .toList();
            profilesToTrash.forEach(Profile::moveToTrash);
            profileRepository.saveAll(profilesToTrash);
        }

        account.changePlan(targetPlan);
        accountRepository.save(account);
    }
}