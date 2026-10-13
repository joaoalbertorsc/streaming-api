package com.ebac.streaming.plan;

import com.ebac.streaming.profile.Profile;

import java.util.List;

public record MigrationPreview(Plan currentPlan, Plan targetPlan, int profilesToRemove,
                               List<Profile> eligibleProfiles) {
}