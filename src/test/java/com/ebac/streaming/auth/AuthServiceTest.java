package com.ebac.streaming.auth;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.account.AccountRepository;
import com.ebac.streaming.exception.EmailAlreadyInUseException;
import com.ebac.streaming.exception.InvalidCredentialsException;
import com.ebac.streaming.profile.Profile;
import com.ebac.streaming.profile.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthService(accountRepository, profileRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerCreatesAccountAndOwnerProfile() {
        when(accountRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(passwordEncoder.encode("1234")).thenReturn("hashedPin");
        when(jwtService.generateToken(any(UUID.class))).thenReturn("a.jwt.token");

        AuthResult result = authService.register(
                "user@example.com", "password123", "1234", "Main");

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getEmail()).isEqualTo("user@example.com");
        assertThat(savedAccount.getPasswordHash()).isEqualTo("hashedPassword");
        assertThat(savedAccount.getPinHash()).isEqualTo("hashedPin");

        ArgumentCaptor<Profile> profileCaptor = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(profileCaptor.capture());
        Profile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.isOwner()).isTrue();

        assertThat(result.token()).isEqualTo("a.jwt.token");
        assertThat(result.accountId()).isEqualTo(savedAccount.getId());
        assertThat(result.defaultProfileId()).isEqualTo(savedProfile.getId());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(accountRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.register("user@example.com", "password123", "1234", "Main"))
                .isInstanceOf(EmailAlreadyInUseException.class);

        verify(accountRepository, never()).save(any());
        verify(profileRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokenOnValidCredentials() {
        Account account = Account.register("user@example.com", "hashedPassword", "hashedPin");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(jwtService.generateToken(account.getId())).thenReturn("a.jwt.token");

        AuthResult result = authService.login("user@example.com", "password123");

        assertThat(result.token()).isEqualTo("a.jwt.token");
        assertThat(result.accountId()).isEqualTo(account.getId());
    }

    @Test
    void loginRejectsWhenEmailNotFound() {
        when(accountRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("missing@example.com", "password123"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsWhenPasswordDoesNotMatch() {
        Account account = Account.register("user@example.com", "hashedPassword", "hashedPin");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(anyString(), eq("hashedPassword"))).thenReturn(false);

        assertThatThrownBy(() -> authService.login("user@example.com", "wrongPassword"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginFailuresDoNotRevealWhetherTheEmailExists() {
        Account account = Account.register("user@example.com", "hashedPassword", "hashedPin");
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(account));
        when(accountRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), eq("hashedPassword"))).thenReturn(false);

        Throwable unknownEmail = catchThrowable(() -> authService.login("missing@example.com", "password123"));
        Throwable wrongPassword = catchThrowable(() -> authService.login("user@example.com", "wrongPassword"));

        assertThat(unknownEmail).isExactlyInstanceOf(wrongPassword.getClass());
        assertThat(unknownEmail).hasMessage(wrongPassword.getMessage());
    }
}