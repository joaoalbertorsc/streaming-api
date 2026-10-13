package com.ebac.streaming.profile;

import java.time.Instant;
import java.util.UUID;

public record TrashedProfileResponse(UUID id, String name, AgeCategory ageCategory, Instant deletedAt) {

    public static TrashedProfileResponse from(Profile profile) {
        return new TrashedProfileResponse(profile.getId(), profile.getName(),
                profile.getAgeCategory(), profile.getDeletedAt());
    }
}