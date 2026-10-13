package com.ebac.streaming.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReviewRequest(
        @NotNull UUID movieId,
        @Min(1) @Max(5) int stars,
        @Size(max = 500) String comment
) {
}