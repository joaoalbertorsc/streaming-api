package com.ebac.streaming.auth;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.account.AccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    public AccountUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String accountId) throws UsernameNotFoundException {
        Account account = accountRepository.findById(java.util.UUID.fromString(accountId))
                .orElseThrow(() -> new UsernameNotFoundException("Account not found."));

        return new User(account.getId().toString(), account.getPasswordHash(),
                List.of(() -> "ROLE_SUBSCRIBER"));
    }
}