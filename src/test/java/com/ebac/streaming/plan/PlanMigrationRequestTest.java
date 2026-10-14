package com.ebac.streaming.plan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanMigrationRequestTest {

    @Test
    void missingProfileListIsTreatedAsEmptySoUpgradesNeedNoBody() {
        PlanMigrationRequest request = new PlanMigrationRequest(Plan.PREMIUM, null);

        assertThat(request.profileIdsToRemove()).isEmpty();
    }
}
