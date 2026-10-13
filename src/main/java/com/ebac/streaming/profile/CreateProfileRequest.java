package com.ebac.streaming.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProfileRequest(
        @NotBlank String name,
        @NotNull AgeCategory ageCategory
) {
}