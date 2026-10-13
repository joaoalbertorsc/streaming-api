package com.ebac.streaming.watch;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.account.AccountRepository;
import com.ebac.streaming.auth.AccountUserDetailsService;
import com.ebac.streaming.auth.CurrentAccountProvider;
import com.ebac.streaming.auth.JwtProperties;
import com.ebac.streaming.auth.JwtService;
import com.ebac.streaming.config.SecurityConfig;
import com.ebac.streaming.exception.ContentNotAllowedException;
import com.ebac.streaming.exception.InvalidPinException;
import com.ebac.streaming.exception.ProfileNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WatchController.class)
@Import({SecurityConfig.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
class WatchControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @MockitoBean private WatchService watchService;
    @MockitoBean private CurrentAccountProvider currentAccountProvider;
    @MockitoBean private AccountRepository accountRepository;
    @MockitoBean private AccountUserDetailsService accountUserDetailsService;

    private Account account;
    private UUID profileId;
    private UUID movieId;
    private String token;

    @BeforeEach
    void setUp() {
        account = Account.register("user@example.com", "hash", "pin");
        profileId = UUID.randomUUID();
        movieId = UUID.randomUUID();
        token = jwtService.generateToken(account.getId());
        when(currentAccountProvider.getCurrentAccount()).thenReturn(account);

        var userDetails = new org.springframework.security.core.userdetails.User(
                account.getId().toString(), "unused", List.of(() -> "ROLE_SUBSCRIBER"));
        when(accountUserDetailsService.loadUserByUsername(account.getId().toString()))
                .thenReturn(userDetails);
    }

    @Test
    void watchReturns200WithoutPinForNonAdultContent() throws Exception {
        WatchResult result = new WatchResult("Title", "Movie ended", Instant.now());
        when(watchService.watch(account, profileId, movieId, null)).thenReturn(result);

        mockMvc.perform(post("/api/v1/watch")
                        .param("profileId", profileId.toString())
                        .param("movieId", movieId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieTitle").value("Title"))
                .andExpect(jsonPath("$.message").value("Movie ended"));
    }

    @Test
    void watchReturns200WithCorrectPinForAdultContent() throws Exception {
        WatchResult result = new WatchResult("Adult Title", "Movie ended", Instant.now());
        when(watchService.watch(account, profileId, movieId, "1234")).thenReturn(result);

        mockMvc.perform(post("/api/v1/watch")
                        .param("profileId", profileId.toString())
                        .param("movieId", movieId.toString())
                        .header("Authorization", "Bearer " + token)
                        .header("X-Account-Pin", "1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movieTitle").value("Adult Title"));
    }

    @Test
    void watchWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/watch")
                        .param("profileId", profileId.toString())
                        .param("movieId", movieId.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void watchMapsForeignProfileTo404() throws Exception {
        when(watchService.watch(account, profileId, movieId, null))
                .thenThrow(new ProfileNotFoundException("Profile not found."));

        mockMvc.perform(post("/api/v1/watch")
                        .param("profileId", profileId.toString())
                        .param("movieId", movieId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void watchMapsBlockedContentTo403() throws Exception {
        when(watchService.watch(account, profileId, movieId, null))
                .thenThrow(new ContentNotAllowedException("Not allowed."));

        mockMvc.perform(post("/api/v1/watch")
                        .param("profileId", profileId.toString())
                        .param("movieId", movieId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void watchMapsMissingPinForAdultContentTo403() throws Exception {
        when(watchService.watch(account, profileId, movieId, null))
                .thenThrow(new InvalidPinException("A PIN is required to watch this content."));

        mockMvc.perform(post("/api/v1/watch")
                        .param("profileId", profileId.toString())
                        .param("movieId", movieId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}