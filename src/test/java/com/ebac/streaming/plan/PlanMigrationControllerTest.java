package com.ebac.streaming.plan;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.account.AccountRepository;
import com.ebac.streaming.auth.AccountUserDetailsService;
import com.ebac.streaming.auth.CurrentAccountProvider;
import com.ebac.streaming.auth.JwtProperties;
import com.ebac.streaming.auth.JwtService;
import com.ebac.streaming.config.SecurityConfig;
import com.ebac.streaming.exception.IllegalPlanMigrationException;
import com.ebac.streaming.profile.AgeCategory;
import com.ebac.streaming.profile.Profile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlanMigrationController.class)
@Import({SecurityConfig.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
class PlanMigrationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;

    @MockitoBean private PlanMigrationService planMigrationService;
    @MockitoBean private CurrentAccountProvider currentAccountProvider;
    @MockitoBean private AccountRepository accountRepository;
    @MockitoBean private AccountUserDetailsService accountUserDetailsService;

    private Account account;
    private String token;

    @BeforeEach
    void setUp() {
        account = Account.register("user@example.com", "hash", "pin");
        account.changePlan(Plan.PREMIUM);
        token = jwtService.generateToken(account.getId());
        when(currentAccountProvider.getCurrentAccount()).thenReturn(account);

        var userDetails = new org.springframework.security.core.userdetails.User(
                account.getId().toString(), "unused", List.of(() -> "ROLE_SUBSCRIBER"));
        when(accountUserDetailsService.loadUserByUsername(account.getId().toString()))
                .thenReturn(userDetails);
    }

    @Test
    void previewReturns200WithEligibleProfiles() throws Exception {
        Profile p1 = Profile.create(account, "P1", AgeCategory.ADULT);
        MigrationPreview preview = new MigrationPreview(Plan.PREMIUM, Plan.STANDARD, 2, List.of(p1));
        when(planMigrationService.preview(account, Plan.STANDARD)).thenReturn(preview);

        mockMvc.perform(get("/api/v1/plan/migration-preview")
                        .param("targetPlan", "STANDARD")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilesToRemove").value(2))
                .andExpect(jsonPath("$.eligibleProfiles[0].name").value("P1"));
    }

    @Test
    void previewWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/plan/migration-preview")
                        .param("targetPlan", "STANDARD"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void migrateReturns200() throws Exception {
        PlanMigrationRequest request = new PlanMigrationRequest(Plan.PREMIUM, List.of());

        mockMvc.perform(post("/api/v1/plan/migration")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM"));
    }

    @Test
    void migrateMapsValidationFailureTo409() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalPlanMigrationException("Invalid migration."))
                .when(planMigrationService).migrate(account, Plan.BASIC, List.of());

        PlanMigrationRequest request = new PlanMigrationRequest(Plan.BASIC, List.of());

        mockMvc.perform(post("/api/v1/plan/migration")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Invalid migration."));
    }

    @Test
    void migrateWithMissingTargetPlanReturns400() throws Exception {
        String invalidJson = "{\"profileIdsToRemove\":[]}";

        mockMvc.perform(post("/api/v1/plan/migration")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    private void authenticateWithoutSubscriberRole() {
        var userWithoutRole = new org.springframework.security.core.userdetails.User(
                account.getId().toString(), "unused", List.of(() -> "ROLE_OTHER"));
        when(accountUserDetailsService.loadUserByUsername(account.getId().toString()))
                .thenReturn(userWithoutRole);
    }

    @Test
    void planMigrationRequiresSubscriberRole() throws Exception {
        authenticateWithoutSubscriberRole();

        mockMvc.perform(get("/api/v1/plan/migration-preview")
                        .param("targetPlan", "STANDARD")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/plan/migration")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"targetPlan":"STANDARD","profileIdsToRemove":[]}
                                """))
                .andExpect(status().isForbidden());
    }
}
