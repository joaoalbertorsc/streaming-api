package com.ebac.streaming.plan;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.auth.CurrentAccountProvider;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plan")
public class PlanMigrationController {

    private final PlanMigrationService planMigrationService;
    private final CurrentAccountProvider currentAccountProvider;

    public PlanMigrationController(PlanMigrationService planMigrationService,
                                   CurrentAccountProvider currentAccountProvider) {
        this.planMigrationService = planMigrationService;
        this.currentAccountProvider = currentAccountProvider;
    }

    @GetMapping("/migration-preview")
    public ResponseEntity<MigrationPreviewResponse> preview(@RequestParam Plan targetPlan) {
        Account account = currentAccountProvider.getCurrentAccount();
        MigrationPreview preview = planMigrationService.preview(account, targetPlan);
        return ResponseEntity.ok(MigrationPreviewResponse.from(preview));
    }

    @PostMapping("/migration")
    public ResponseEntity<AccountResponse> migrate(@Valid @RequestBody PlanMigrationRequest request) {
        Account account = currentAccountProvider.getCurrentAccount();
        planMigrationService.migrate(account, request.targetPlan(), request.profileIdsToRemove());
        return ResponseEntity.ok(AccountResponse.from(account));
    }
}