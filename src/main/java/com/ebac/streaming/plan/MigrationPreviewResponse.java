package com.ebac.streaming.plan;

import com.ebac.streaming.profile.ProfileResponse;

import java.util.List;

public record MigrationPreviewResponse(Plan currentPlan, Plan targetPlan, int profilesToRemove,
                                       List<ProfileResponse> eligibleProfiles) {

    public static MigrationPreviewResponse from(MigrationPreview preview) {
        List<ProfileResponse> eligible = preview.eligibleProfiles().stream()
                .map(ProfileResponse::from)
                .toList();
        return new MigrationPreviewResponse(preview.currentPlan(), preview.targetPlan(),
                preview.profilesToRemove(), eligible);
    }
}