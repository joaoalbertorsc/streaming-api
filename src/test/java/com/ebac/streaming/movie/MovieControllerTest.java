package com.ebac.streaming.movie;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.account.AccountRepository;
import com.ebac.streaming.auth.AccountUserDetailsService;
import com.ebac.streaming.auth.CurrentAccountProvider;
import com.ebac.streaming.auth.JwtProperties;
import com.ebac.streaming.auth.JwtService;
import com.ebac.streaming.config.SecurityConfig;
import com.ebac.streaming.exception.ContentNotAllowedException;
import com.ebac.streaming.exception.MovieNotFoundException;
import com.ebac.streaming.exception.ProfileNotFoundException;
import com.ebac.streaming.plan.Plan;
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

@WebMvcTest(MovieController.class)
@Import({SecurityConfig.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
class MovieControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    @MockitoBean private MovieService movieService;
    @MockitoBean private ProfileAccessService profileAccessService;
    @MockitoBean private CurrentAccountProvider currentAccountProvider;
    @MockitoBean private AccountRepository accountRepository;
    @MockitoBean private AccountUserDetailsService accountUserDetailsService;

    private Account account;
    private Profile profile;
    private String token;

    @BeforeEach
    void setUp() {
        account = Account.register("user@example.com", "hash", "pin");
        account.changePlan(Plan.PREMIUM);
        profile = Profile.createOwner(account, "Main");
        token = jwtService.generateToken(account.getId());
        when(currentAccountProvider.getCurrentAccount()).thenReturn(account);

        var userDetails = new org.springframework.security.core.userdetails.User(
                account.getId().toString(), "unused", List.of(() -> "ROLE_SUBSCRIBER"));
        when(accountUserDetailsService.loadUserByUsername(account.getId().toString()))
                .thenReturn(userDetails);
    }

    private Movie movie() {
        return new Movie("Title", "Genre", "Synopsis", 2020, "Cast", 100,
                ContentRating.L, Plan.BASIC);
    }

    @Test
    void listReturns200WithMovies() throws Exception {
        when(profileAccessService.getActiveOwnedProfile(account, profile.getId())).thenReturn(profile);
        when(movieService.listAllowed(account.getPlan(), profile.getAgeCategory()))
                .thenReturn(List.of(movie()));

        mockMvc.perform(get("/api/v1/movies")
                        .param("profileId", profile.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Title"));
    }

    @Test
    void listWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/movies")
                        .param("profileId", profile.getId().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listMapsForeignProfileTo404() throws Exception {
        UUID randomProfileId = UUID.randomUUID();
        when(profileAccessService.getActiveOwnedProfile(account, randomProfileId))
                .thenThrow(new ProfileNotFoundException("Profile not found."));

        mockMvc.perform(get("/api/v1/movies")
                        .param("profileId", randomProfileId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReturns200WithMovie() throws Exception {
        Movie movie = movie();
        when(profileAccessService.getActiveOwnedProfile(account, profile.getId())).thenReturn(profile);
        when(movieService.getAllowed(movie.getId(), account.getPlan(), profile.getAgeCategory()))
                .thenReturn(movie);

        mockMvc.perform(get("/api/v1/movies/" + movie.getId())
                        .param("profileId", profile.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Title"));
    }

    @Test
    void getMapsNotFoundMovieTo404() throws Exception {
        UUID randomMovieId = UUID.randomUUID();
        when(profileAccessService.getActiveOwnedProfile(account, profile.getId())).thenReturn(profile);
        when(movieService.getAllowed(randomMovieId, account.getPlan(), profile.getAgeCategory()))
                .thenThrow(new MovieNotFoundException("Movie not found."));

        mockMvc.perform(get("/api/v1/movies/" + randomMovieId)
                        .param("profileId", profile.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMapsBlockedContentTo403() throws Exception {
        Movie movie = movie();
        when(profileAccessService.getActiveOwnedProfile(account, profile.getId())).thenReturn(profile);
        when(movieService.getAllowed(movie.getId(), account.getPlan(), profile.getAgeCategory()))
                .thenThrow(new ContentNotAllowedException("Not allowed."));

        mockMvc.perform(get("/api/v1/movies/" + movie.getId())
                        .param("profileId", profile.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileScopedEndpointsOnlyRequireAuthenticationNotTheSubscriberRole() throws Exception {
        var userWithoutRole = new org.springframework.security.core.userdetails.User(
                account.getId().toString(), "unused", List.of(() -> "ROLE_OTHER"));
        when(accountUserDetailsService.loadUserByUsername(account.getId().toString()))
                .thenReturn(userWithoutRole);
        when(profileAccessService.getActiveOwnedProfile(account, profile.getId())).thenReturn(profile);
        when(movieService.listAllowed(account.getPlan(), profile.getAgeCategory())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/movies")
                        .param("profileId", profile.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
