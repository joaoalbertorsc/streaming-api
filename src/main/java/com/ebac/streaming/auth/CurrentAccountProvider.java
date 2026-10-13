package com.ebac.streaming.auth;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.account.AccountRepository;
import com.ebac.streaming.exception.AccountNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentAccountProvider {

    private final AccountRepository accountRepository;

    public CurrentAccountProvider(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account getCurrentAccount() {
        UserDetails principal = (UserDetails) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        UUID accountId = UUID.fromString(principal.getUsername());

        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Authenticated account no longer exists."));
    }
}