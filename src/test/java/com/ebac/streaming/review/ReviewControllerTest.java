package com.ebac.streaming.review;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.account.AccountRepository;
import com.ebac.streaming.auth.AccountUserDetailsService;
import com.ebac.streaming.auth.CurrentAccountProvider;
import com.ebac.streaming.auth.JwtProperties;
import com.ebac.streaming.auth.JwtService;
import com.ebac.streaming.config.SecurityConfig;
import com.ebac.streaming.exception.MovieNotFoundException;
import com.ebac.streaming.exception.MovieNotWatchedException;
import com.ebac.streaming.exception.ReviewNotFoundException;
import com.ebac.streaming.movie.ContentRating;
import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.movie.MovieRepository;
import com.ebac.streaming.plan.Plan;
import com.ebac.streaming.profile.Profile;
import com.ebac.streaming.profile.ProfileAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ebac.streaming.exception.ProfileNotFoundException;
import com.ebac.streaming.exception.ReviewAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
@Import({SecurityConfig.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
class ReviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;

    @MockitoBean private ReviewService reviewService;
    @MockitoBean private ProfileAccessService profileAccessService;
    @MockitoBean private MovieRepository movieRepository;
    @MockitoBean private CurrentAccountProvider currentAccountProvider;
    @MockitoBean private AccountRepository accountRepository;
    @MockitoBean private AccountUserDetailsService accountUserDetailsService;

    private Account account;
    private Profile profile;
    private Movie movie;
    private String token;

    @BeforeEach
    void setUp() {
        account = Account.register("user@example.com", "hash", "pin");
        profile = Profile.createOwner(account, "Main");
        movie = new Movie("Title", "Genre", "Synopsis", 2020, "Cast", 100,
                ContentRating.L, Plan.BASIC);
        token = jwtService.generateToken(account.getId());
        when(currentAccountProvider.getCurrentAccount()).thenReturn(account);
        when(profileAccessService.getActiveOwnedProfile(account, profile.getId())).thenReturn(profile);

        var userDetails = new org.springframework.security.core.userdetails.User(
                account.getId().toString(), "unused", List.of(() -> "ROLE_SUBSCRIBER"));
        when(accountUserDetailsService.loadUserByUsername(account.getId().toString()))
                .thenReturn(userDetails);
    }

    @Test
    void createReturns201() throws Exception {
        when(movieRepository.findById(movie.getId())).thenReturn(Optional.of(movie));
        Review review = new Review(profile, movie, 5, "Great!");
        when(reviewService.create(profile, movie, 5, "Great!")).thenReturn(review);

        ReviewRequest request = new ReviewRequest(movie.getId(), 5, "Great!");

        mockMvc.perform(post("/api/v1/reviews")
                        .param("profileId", profile.getId().toString())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stars").value(5));
    }

    @Test
    void createWithoutTokenReturns401() throws Exception {
        ReviewRequest request = new ReviewRequest(movie.getId(), 5, "Great!");

        mockMvc.perform(post("/api/v1/reviews")
                        .param("profileId", profile.getId().toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithInvalidStarsReturns400() throws Exception {
        ReviewRequest request = new ReviewRequest(movie.getId(), 7, "Great!");

        mockMvc.perform(post("/api/v1/reviews")
                        .param("profileId", profile.getId().toString())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMapsMovieNotFoundTo404() throws Exception {
        UUID randomMovieId = UUID.randomUUID();
        when(movieRepository.findById(randomMovieId)).thenReturn(Optional.empty());

        ReviewRequest request = new ReviewRequest(randomMovieId, 5, "Great!");

        mockMvc.perform(post("/api/v1/reviews")
                        .param("profileId", profile.getId().toString())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createMapsNotWatchedTo409() throws Exception {
        when(movieRepository.findById(movie.getId())).thenReturn(Optional.of(movie));
        when(reviewService.create(profile, movie, 5, "Great!"))
                .thenThrow(new MovieNotWatchedException("Not watched."));

        ReviewRequest request = new ReviewRequest(movie.getId(), 5, "Great!");

        mockMvc.perform(post("/api/v1/reviews")
                        .param("profileId", profile.getId().toString())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateReturns200() throws Exception {
        Review review = new Review(profile, movie, 3, "Ok");
        when(reviewService.update(profile, review.getId(), 5, "Now great!")).thenReturn(review);

        ReviewUpdateRequest request = new ReviewUpdateRequest(5, "Now great!");

        mockMvc.perform(put("/api/v1/reviews/" + review.getId())
                        .param("profileId", profile.getId().toString())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void updateMapsNotOwnedTo404() throws Exception {
        UUID reviewId = UUID.randomUUID();
        when(reviewService.update(profile, reviewId, 5, "Changed"))
                .thenThrow(new ReviewNotFoundException("Review not found."));

        ReviewUpdateRequest request = new ReviewUpdateRequest(5, "Changed");

        mockMvc.perform(put("/api/v1/reviews/" + reviewId)
                        .param("profileId", profile.getId().toString())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturns200() throws Exception {
        Review review = new Review(profile, movie, 4, "Nice");
        when(reviewService.list(profile)).thenReturn(List.of(review));

        mockMvc.perform(get("/api/v1/reviews")
                        .param("profileId", profile.getId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stars").value(4));
    }

    @Test
    void createMapsDuplicateReviewTo409() throws Exception {
        when(movieRepository.findById(movie.getId())).thenReturn(Optional.of(movie));
        when(reviewService.create(profile, movie, 5, "Great!"))
                .thenThrow(new ReviewAlreadyExistsException("Already reviewed."));

        mockMvc.perform(post("/api/v1/reviews")
                        .param("profileId", profile.getId().toString())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReviewRequest(movie.getId(), 5, "Great!"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Already reviewed."));
    }

    @Test
    void createWithForeignProfileReturns404() throws Exception {
        UUID foreignProfileId = UUID.randomUUID();
        when(profileAccessService.getActiveOwnedProfile(account, foreignProfileId))
                .thenThrow(new ProfileNotFoundException("Profile not found."));

        mockMvc.perform(post("/api/v1/reviews")
                        .param("profileId", foreignProfileId.toString())
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ReviewRequest(movie.getId(), 5, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void listWithForeignProfileReturns404() throws Exception {
        UUID foreignProfileId = UUID.randomUUID();
        when(profileAccessService.getActiveOwnedProfile(account, foreignProfileId))
                .thenThrow(new ProfileNotFoundException("Profile not found."));

        mockMvc.perform(get("/api/v1/reviews")
                        .param("profileId", foreignProfileId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
