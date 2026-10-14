package com.ebac.streaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ebac.streaming.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StreamingApiIntegrationTest extends PostgresTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static final UUID PREMIUM_SIXTEEN_MOVIE = UUID.fromString("10000000-0000-0000-0000-000000000012"); // Afterparty
    private static final UUID PREMIUM_EIGHTEEN_MOVIE = UUID.fromString("10000000-0000-0000-0000-000000000015"); // Nightshift at the Aurelia
    private static final UUID BASIC_L_MOVIE = UUID.fromString("10000000-0000-0000-0000-000000000001"); // The Quiet Orchard
    private static final UUID STANDARD_TEN_MOVIE = UUID.fromString("10000000-0000-0000-0000-000000000005"); // Static Lines

    private String registerAndGetToken(String email, String pin) throws Exception {
        String body = """
                {"email":"%s","password":"password123","pin":"%s","defaultProfileName":"Main"}
                """.formatted(email, pin);

        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("token").asText();
    }

    private UUID defaultProfileId(String token) throws Exception {
        String response = mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();

        JsonNode profiles = objectMapper.readTree(response);
        for (JsonNode profile : profiles) {
            if (profile.get("owner").asBoolean()) {
                return UUID.fromString(profile.get("id").asText());
            }
        }
        throw new IllegalStateException("No owner profile found.");
    }

    // Flow 1: register -> upgrade to Premium -> create 3 profiles -> downgrade to Standard -> 2 trashed, plan changed
    @Test
    void downgradeFlowTrashesCorrectProfilesAndChangesPlan() throws Exception {
        String token = registerAndGetToken("downgrade@example.com", "1234");

        mockMvc.perform(post("/api/v1/plan/migration")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"targetPlan":"PREMIUM","profileIdsToRemove":[]}
                                """))
                .andExpect(status().isOk());

        List<UUID> nonOwnerProfileIds = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String response = mockMvc.perform(post("/api/v1/profiles")
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content("""
                                    {"name":"Profile %d","ageCategory":"ADULT"}
                                    """.formatted(i)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            nonOwnerProfileIds.add(UUID.fromString(objectMapper.readTree(response).get("id").asText()));
        }

        String preview = mockMvc.perform(get("/api/v1/plan/migration-preview")
                        .param("targetPlan", "STANDARD")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(preview).get("profilesToRemove").asInt()).isEqualTo(2);

        String toRemove = objectMapper.writeValueAsString(nonOwnerProfileIds.subList(0, 2));
        mockMvc.perform(post("/api/v1/plan/migration")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"targetPlan":"STANDARD","profileIdsToRemove":%s}
                                """.formatted(toRemove)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("STANDARD"));

        String activeProfiles = mockMvc.perform(get("/api/v1/profiles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(activeProfiles)).hasSize(2); // owner + 1 surviving profile
    }

    // Flow 2: register -> watch allowed movie -> check history -> watch again -> 2 entries, most recent first
    @Test
    void repeatWatchCreatesSeparateHistoryEntriesOrderedByMostRecent() throws Exception {
        String token = registerAndGetToken("repeatwatch@example.com", "1234");
        UUID profileId = defaultProfileId(token);

        mockMvc.perform(post("/api/v1/watch")
                        .param("profileId", profileId.toString())
                        .param("movieId", BASIC_L_MOVIE.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Thread.sleep(10); // ensure a distinct, later timestamp

        mockMvc.perform(post("/api/v1/watch")
                        .param("profileId", profileId.toString())
                        .param("movieId", BASIC_L_MOVIE.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String history = mockMvc.perform(get("/api/v1/history")
                        .param("profileId", profileId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode entries = objectMapper.readTree(history);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).get("movieTitle").asText()).isEqualTo("The Quiet Orchard");
    }

    // Flow 3: watch 18-rated movie without PIN -> 403 -> retry with correct PIN -> success -> review -> update review
    @Test
    void adultContentRequiresPinThenAllowsReviewAndUpdate() throws Exception {
        String token = registerAndGetToken("pinflow@example.com", "9999");
        UUID profileId = defaultProfileId(token);

        // The 18-rated seed movie is Premium-only; upgrade first so the plan rule passes
        // and the 403 below can only come from the missing PIN.
        mockMvc.perform(post("/api/v1/plan/migration")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"targetPlan":"PREMIUM","profileIdsToRemove":[]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/watch")
                        .param("profileId", profileId.toString())
                        .param("movieId", PREMIUM_EIGHTEEN_MOVIE.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("A PIN is required to watch this content."));

        mockMvc.perform(post("/api/v1/watch")
                        .param("profileId", profileId.toString())
                        .param("movieId", PREMIUM_EIGHTEEN_MOVIE.toString())
                        .header("Authorization", "Bearer " + token)
                        .header("X-Account-Pin", "9999"))
                .andExpect(status().isOk());

        String reviewResponse = mockMvc.perform(post("/api/v1/reviews")
                        .param("profileId", profileId.toString())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"movieId":"%s","stars":4,"comment":"Tense and well shot."}
                                """.formatted(PREMIUM_EIGHTEEN_MOVIE)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID reviewId = UUID.fromString(objectMapper.readTree(reviewResponse).get("id").asText());

        mockMvc.perform(put("/api/v1/reviews/" + reviewId)
                        .param("profileId", profileId.toString())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"stars":5,"comment":"Even better on a rewatch."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stars").value(5));
    }

    // Flow 4: restore after quota freed succeeds; restore while quota full fails
    @Test
    void restoreRespectsQuota() throws Exception {
        String token = registerAndGetToken("restoreflow@example.com", "1234");

        mockMvc.perform(post("/api/v1/plan/migration")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"targetPlan":"STANDARD","profileIdsToRemove":[]}
                                """))
                .andExpect(status().isOk());

        String createdProfile = mockMvc.perform(post("/api/v1/profiles")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"name":"Kid","ageCategory":"CHILD"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID profileId = UUID.fromString(objectMapper.readTree(createdProfile).get("id").asText());

        mockMvc.perform(delete("/api/v1/profiles/" + profileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Quota is free (owner + 1 trashed, Standard allows 2) - restore should succeed.
        mockMvc.perform(post("/api/v1/profiles/" + profileId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Trash it again, then fill the now-free slot with a different profile so the
        // account is back at its Standard-plan limit of 2 active profiles.
        mockMvc.perform(delete("/api/v1/profiles/" + profileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/profiles")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"name":"Another","ageCategory":"ADULT"}
                                """))
                .andExpect(status().isCreated());

        // Quota is now full again - restoring the trashed profile must fail.
        mockMvc.perform(post("/api/v1/profiles/" + profileId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }
}