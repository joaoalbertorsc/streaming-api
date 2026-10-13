package com.ebac.streaming.plan;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PlanMigrationRequest(
        @NotNull Plan targetPlan,
        List<UUID> profileIdsToRemove
) {
    public PlanMigrationRequest {
        if (profileIdsToRemove == null) {
            profileIdsToRemove = List.of();
        }
    }
}