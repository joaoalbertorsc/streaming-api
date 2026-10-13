package com.ebac.streaming.profile;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.auth.CurrentAccountProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    private final ProfileService profileService;
    private final CurrentAccountProvider currentAccountProvider;

    public ProfileController(ProfileService profileService, CurrentAccountProvider currentAccountProvider) {
        this.profileService = profileService;
        this.currentAccountProvider = currentAccountProvider;
    }

    @PostMapping
    public ResponseEntity<ProfileResponse> create(@Valid @RequestBody CreateProfileRequest request) {
        Account account = currentAccountProvider.getCurrentAccount();
        Profile profile = profileService.create(account, request.name(), request.ageCategory());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profile));
    }

    @GetMapping
    public ResponseEntity<List<ProfileResponse>> list() {
        Account account = currentAccountProvider.getCurrentAccount();
        List<ProfileResponse> profiles = profileService.listActive(account).stream()
                .map(ProfileResponse::from)
                .toList();
        return ResponseEntity.ok(profiles);
    }

    @DeleteMapping("/{profileId}")
    public ResponseEntity<Void> delete(@PathVariable UUID profileId) {
        Account account = currentAccountProvider.getCurrentAccount();
        profileService.delete(account, profileId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/trash")
    public ResponseEntity<List<TrashedProfileResponse>> listTrash() {
        Account account = currentAccountProvider.getCurrentAccount();
        List<TrashedProfileResponse> profiles = profileService.listTrashed(account).stream()
                .map(TrashedProfileResponse::from)
                .toList();
        return ResponseEntity.ok(profiles);
    }

    @PostMapping("/{profileId}/restore")
    public ResponseEntity<ProfileResponse> restore(@PathVariable UUID profileId) {
        Account account = currentAccountProvider.getCurrentAccount();
        Profile profile = profileService.restore(account, profileId);
        return ResponseEntity.ok(ProfileResponse.from(profile));
    }
}