package com.ebac.streaming.watch;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.auth.CurrentAccountProvider;
import com.ebac.streaming.profile.Profile;
import com.ebac.streaming.profile.ProfileAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final HistoryService historyService;
    private final ProfileAccessService profileAccessService;
    private final CurrentAccountProvider currentAccountProvider;

    public HistoryController(HistoryService historyService, ProfileAccessService profileAccessService,
                             CurrentAccountProvider currentAccountProvider) {
        this.historyService = historyService;
        this.profileAccessService = profileAccessService;
        this.currentAccountProvider = currentAccountProvider;
    }

    @GetMapping
    public ResponseEntity<List<HistoryEntryResponse>> list(@RequestParam UUID profileId) {
        Account account = currentAccountProvider.getCurrentAccount();
        Profile profile = profileAccessService.getActiveOwnedProfile(account, profileId);

        List<HistoryEntryResponse> history = historyService.getHistory(profile).stream()
                .map(HistoryEntryResponse::from)
                .toList();
        return ResponseEntity.ok(history);
    }
}
