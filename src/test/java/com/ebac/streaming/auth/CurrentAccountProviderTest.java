package com.ebac.streaming.auth;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.account.AccountRepository;
import com.ebac.streaming.exception.AccountNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class CurrentAccountProviderTest {

    @Mock private AccountRepository accountRepository;

    private CurrentAccountProvider provider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        provider = new CurrentAccountProvider(accountRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheAuthenticatedAccount() {
        Account account = Account.register("user@example.com", "hash", "pin");
        authenticateAs(account.getId());
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        Account result = provider.getCurrentAccount();

        assertThat(result).isEqualTo(account);
    }

    @Test
    void throwsWhenAuthenticatedAccountNoLongerExists() {
        UUID accountId = UUID.randomUUID();
        authenticateAs(accountId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.getCurrentAccount())
                .isInstanceOf(AccountNotFoundException.class);
    }

    private void authenticateAs(UUID accountId) {
        var userDetails = new org.springframework.security.core.userdetails.User(
                accountId.toString(), "unused", java.util.List.of());
        var authToken = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}