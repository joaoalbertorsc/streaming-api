package com.ebac.streaming.watch;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.auth.CurrentAccountProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/watch")
public class WatchController {

    private final WatchService watchService;
    private final CurrentAccountProvider currentAccountProvider;

    public WatchController(WatchService watchService, CurrentAccountProvider currentAccountProvider) {
        this.watchService = watchService;
        this.currentAccountProvider = currentAccountProvider;
    }

    @PostMapping
    public ResponseEntity<WatchResponse> watch(@RequestParam UUID profileId, @RequestParam UUID movieId,
                                               @RequestHeader(value = "X-Account-Pin", required = false) String pin) {
        Account account = currentAccountProvider.getCurrentAccount();
        WatchResult result = watchService.watch(account, profileId, movieId, pin);
        return ResponseEntity.ok(WatchResponse.from(result));
    }
}