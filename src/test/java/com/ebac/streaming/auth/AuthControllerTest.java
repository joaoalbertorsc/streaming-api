package com.ebac.streaming.auth;

import com.ebac.streaming.exception.EmailAlreadyInUseException;
import com.ebac.streaming.exception.InvalidCredentialsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = com.ebac.streaming.auth.JwtAuthenticationFilter.class)
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AuthService authService;

    @Test
    void registerReturns201WithBody() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        when(authService.register("user@example.com", "password123", "1234", "Main"))
                .thenReturn(new AuthResult("a.jwt.token", accountId, profileId));

        RegisterRequest request = new RegisterRequest(
                "user@example.com", "password123", "1234", "Main");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("a.jwt.token"))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.defaultProfileId").value(profileId.toString()));
    }

    @Test
    void registerRejectsInvalidPayloadWith400() throws Exception {
        RegisterRequest invalidRequest = new RegisterRequest(
                "not-an-email", "short", "abc", "");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void registerMapsDuplicateEmailTo409() throws Exception {
        when(authService.register("user@example.com", "password123", "1234", "Main"))
                .thenThrow(new EmailAlreadyInUseException("This email is already registered."));

        RegisterRequest request = new RegisterRequest(
                "user@example.com", "password123", "1234", "Main");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This email is already registered."));
    }

    @Test
    void loginReturns200WithToken() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(authService.login("user@example.com", "password123"))
                .thenReturn(new AuthResult("a.jwt.token", accountId, null));

        LoginRequest request = new LoginRequest("user@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("a.jwt.token"));
    }

    @Test
    void loginMapsInvalidCredentialsTo401() throws Exception {
        when(authService.login("user@example.com", "wrongPassword"))
                .thenThrow(new InvalidCredentialsException("Invalid email or password."));

        LoginRequest request = new LoginRequest("user@example.com", "wrongPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }
}