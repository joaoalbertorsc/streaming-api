package com.ebac.streaming.plan;

public enum Plan {
    BASIC(1, 0),
    STANDARD(2, 1),
    PREMIUM(4, 2);

    private final int maxProfiles;
    private final int tier;

    Plan(int maxProfiles, int tier) {
        this.maxProfiles = maxProfiles;
        this.tier = tier;
    }

    public int maxProfiles() {
        return maxProfiles;
    }

    public boolean allows(Plan requiredPlan) {
        return this.tier >= requiredPlan.tier;
    }
}
