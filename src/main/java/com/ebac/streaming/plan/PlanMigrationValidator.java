package com.ebac.streaming.plan;

import com.ebac.streaming.exception.IllegalPlanMigrationException;
import com.ebac.streaming.profile.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PlanMigrationValidator {

    public void validate(Plan currentPlan, Plan targetPlan, List<UUID> profileIdsToRemove,
                         List<Profile> activeProfiles) {
        if (currentPlan == targetPlan) {
            throw new IllegalPlanMigrationException("The account is already on this plan.");
        }

        if (hasDuplicates(profileIdsToRemove)) {
            throw new IllegalPlanMigrationException("The list of profiles to remove contains duplicates.");
        }

        boolean isUpgrade = targetPlan.maxProfiles() > currentPlan.maxProfiles();
        if (isUpgrade) {
            if (!profileIdsToRemove.isEmpty()) {
                throw new IllegalPlanMigrationException("An upgrade does not require removing profiles.");
            }
            return;
        }

        int requiredRemovals = activeProfiles.size() - targetPlan.maxProfiles();
        if (profileIdsToRemove.size() != requiredRemovals) {
            throw new IllegalPlanMigrationException(
                    "Exactly " + requiredRemovals + " profile(s) must be removed for this downgrade.");
        }

        Map<UUID, Profile> activeById = activeProfiles.stream()
                .collect(Collectors.toMap(Profile::getId, p -> p));

        for (UUID id : profileIdsToRemove) {
            Profile profile = activeById.get(id);
            if (profile == null) {
                throw new IllegalPlanMigrationException(
                        "Profile " + id + " does not belong to this account or is not active.");
            }
            if (profile.isOwner()) {
                throw new IllegalPlanMigrationException("The owner profile cannot be removed.");
            }
        }
    }

    private boolean hasDuplicates(List<UUID> ids) {
        return ids.size() != Set.copyOf(ids).size();
    }
}