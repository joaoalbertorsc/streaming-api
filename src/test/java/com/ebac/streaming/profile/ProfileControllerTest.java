package com.ebac.streaming.profile;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.account.AccountRepository;
import com.ebac.streaming.auth.AccountUserDetailsService;
import com.ebac.streaming.auth.CurrentAccountProvider;
import com.ebac.streaming.auth.JwtProperties;
import com.ebac.streaming.auth.JwtService;
import com.ebac.streaming.config.SecurityConfig;
import com.ebac.streaming.exception.ProfileQuotaExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ebac.streaming.exception.AccountNotFoundException;
import com.ebac.streaming.exception.IllegalProfileOperationException;
import com.ebac.streaming.exception.ProfileNameConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
class ProfileControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;

    @MockitoBean private ProfileService profileService;
    @MockitoBean private CurrentAccountProvider currentAccountProvider;
    @MockitoBean private AccountRepository accountRepository;
    @MockitoBean private AccountUserDetailsService accountUserDetailsService;


    private Account account;
    private String token;

    @BeforeEach
    void setUp() {
        account = Account.register("user@example.com", "hash", "pin");
        token = jwtService.generateToken(account.getId());
        when(currentAccountProvider.getCurrentAccount()).thenReturn(account);

        var userDetails = new org.springframework.security.core.userdetails.User(
                account.getId().toString(), "unused",
                List.of(() -> "ROLE_SUBSCRIBER"));
        when(accountUserDetailsService.loadUserByUsername(account.getId().toString()))
                .thenReturn(userDetails);
    }

    @Test
    void createReturns201() throws Exception {
        Profile profile = Profile.create(account, "Kids", AgeCategory.CHILD);
        when(profileService.create(account, "Kids", AgeCategory.CHILD)).thenReturn(profile);

        CreateProfileRequest request = new CreateProfileRequest("Kids", AgeCategory.CHILD);

        mockMvc.perform(post("/api/v1/profiles")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Kids"));
    }

    @Test
    void createWithoutTokenReturns401() throws Exception {
        CreateProfileRequest request = new CreateProfileRequest("Kids", AgeCategory.CHILD);

        mockMvc.perform(post("/api/v1/profiles")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithBlankNameReturns400() throws Exception {
        CreateProfileRequest request = new CreateProfileRequest("", AgeCategory.CHILD);

        mockMvc.perform(post("/api/v1/profiles")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMapsQuotaExceededTo409() throws Exception {
        when(profileService.create(account, "Kids", AgeCategory.CHILD))
                .thenThrow(new ProfileQuotaExceededException("Quota exceeded."));

        CreateProfileRequest request = new CreateProfileRequest("Kids", AgeCategory.CHILD);

        mockMvc.perform(post("/api/v1/profiles")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void listReturnsActiveProfiles() throws Exception {
        Profile profile = Profile.createOwner(account, "Main");
        when(profileService.listActive(account)).thenReturn(List.of(profile));

        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Main"));
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID profileId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/profiles/" + profileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void listTrashReturnsTrashedProfiles() throws Exception {
        Profile trashed = Profile.create(account, "Kids", AgeCategory.CHILD);
        trashed.moveToTrash();
        when(profileService.listTrashed(account)).thenReturn(List.of(trashed));

        mockMvc.perform(get("/api/v1/profiles/trash")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kids"));
    }

    @Test
    void restoreReturns200() throws Exception {
        Profile trashed = Profile.create(account, "Kids", AgeCategory.CHILD);
        trashed.moveToTrash();
        when(profileService.restore(account, trashed.getId())).thenReturn(trashed);
        trashed.restore();

        mockMvc.perform(post("/api/v1/profiles/" + trashed.getId() + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kids"));
    }

    @Test
    void deleteOwnerProfileReturns400WithTheApiErrorBody() throws Exception {
        UUID ownerId = UUID.randomUUID();
        doThrow(new IllegalProfileOperationException("The owner profile cannot be deleted."))
                .when(profileService).delete(account, ownerId);

        mockMvc.perform(delete("/api/v1/profiles/" + ownerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("The owner profile cannot be deleted."))
                .andExpect(jsonPath("$.path").value("/api/v1/profiles/" + ownerId))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void restoreMapsQuotaExceededTo409() throws Exception {
        UUID profileId = UUID.randomUUID();
        when(profileService.restore(account, profileId))
                .thenThrow(new ProfileQuotaExceededException("Quota exceeded."));

        mockMvc.perform(post("/api/v1/profiles/" + profileId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Quota exceeded."));
    }

    @Test
    void restoreMapsNameConflictTo409() throws Exception {
        UUID profileId = UUID.randomUUID();
        when(profileService.restore(account, profileId))
                .thenThrow(new ProfileNameConflictException("An active profile with this name already exists."));

        mockMvc.perform(post("/api/v1/profiles/" + profileId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An active profile with this name already exists."));
    }

    @Test
    void validTokenForAnAccountThatNoLongerExistsReturns401() throws Exception {
        when(currentAccountProvider.getCurrentAccount())
                .thenThrow(new AccountNotFoundException("Account not found."));

        mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
