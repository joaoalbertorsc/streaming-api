package com.ebac.streaming.profile;

import java.util.UUID;

public record ProfileResponse(UUID id, String name, AgeCategory ageCategory, boolean owner) {

    public static ProfileResponse from(Profile profile) {
        return new ProfileResponse(profile.getId(), profile.getName(),
                profile.getAgeCategory(), profile.isOwner());
    }
}