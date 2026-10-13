package com.ebac.streaming.watch;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.account.AccountRepository;
import com.ebac.streaming.auth.AccountUserDetailsService;
import com.ebac.streaming.auth.CurrentAccountProvider;
import com.ebac.streaming.auth.JwtProperties;
import com.ebac.streaming.auth.JwtService;
import com.ebac.streaming.config.SecurityConfig;
import com.ebac.streaming.exception.ProfileNotFoundException;
import com.ebac.streaming.movie.ContentRating;
import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.plan.Plan;
import com.ebac.streaming.profile.AgeCategory;
import com.ebac.streaming.profile.Profile;
import com.ebac.streaming.profile.ProfileAccessService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HistoryController.class)
@Import({SecurityConfig.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
class HistoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @MockitoBean private HistoryService historyService;
    @MockitoBean private ProfileAccessService profileAccessService;
    @MockitoBean private CurrentAccountProvider currentAccountProvider;
    @MockitoBean private AccountRepository accountRepository;
    @MockitoBean private AccountUserDetailsService accountUserDetailsService;

    private Account account;
    private UUID profileId;
    private String token;

    @BeforeEach
    void setUp() {
        account = Account.register("user@example.com", "hash", "pin");
        profileId = UUID.randomUUID();
        token = jwtService.generateToken(account.getId());
        when(currentAccountProvider.getCurrentAccount()).thenReturn(account);

        var userDetails = new org.springframework.security.core.userdetails.User(
                account.getId().toString(), "unused", List.of(() -> "ROLE_SUBSCRIBER"));
        when(accountUserDetailsService.loadUserByUsername(account.getId().toString()))
                .thenReturn(userDetails);
    }

    private Movie movie(String title) {
        return new Movie(title, "Genre", "Synopsis", 2020, "Cast", 100, ContentRating.L, Plan.BASIC);
    }

    @Test
    void historyReturns200WithEntriesInServiceOrderIncludingRepeats() throws Exception {
        Profile profile = Profile.create(account, "Kid", AgeCategory.CHILD);
        Movie x = movie("Movie X");
        Movie y = movie("Movie Y");
        when(profileAccessService.getActiveOwnedProfile(account, profileId)).thenReturn(profile);
        // The service already returns most-recent-first; the controller must keep that order.
        when(historyService.getHistory(profile)).thenReturn(List.of(
                new WatchHistoryEntry(profile, x),
                new WatchHistoryEntry(profile, y),
                new WatchHistoryEntry(profile, x)));

        mockMvc.perform(get("/api/v1/history")
                        .param("profileId", profileId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].movieTitle").value("Movie X"))
                .andExpect(jsonPath("$[1].movieTitle").value("Movie Y"))
                .andExpect(jsonPath("$[2].movieTitle").value("Movie X"))
                .andExpect(jsonPath("$[0].movieId").value(x.getId().toString()))
                .andExpect(jsonPath("$[0].watchedAt").exists());
    }

    @Test
    void historyReturnsEmptyListWhenNothingWatched() throws Exception {
        Profile profile = Profile.create(account, "Kid", AgeCategory.CHILD);
        when(profileAccessService.getActiveOwnedProfile(account, profileId)).thenReturn(profile);
        when(historyService.getHistory(profile)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/history")
                        .param("profileId", profileId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void historyWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/history")
                        .param("profileId", profileId.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historyMapsForeignOrTrashedProfileTo404() throws Exception {
        when(profileAccessService.getActiveOwnedProfile(account, profileId))
                .thenThrow(new ProfileNotFoundException("Profile not found."));

        mockMvc.perform(get("/api/v1/history")
                        .param("profileId", profileId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyWithoutProfileIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/history")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
