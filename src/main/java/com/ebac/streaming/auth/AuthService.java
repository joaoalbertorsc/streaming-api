package com.ebac.streaming.auth;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.account.AccountRepository;
import com.ebac.streaming.exception.EmailAlreadyInUseException;
import com.ebac.streaming.exception.InvalidCredentialsException;
import com.ebac.streaming.profile.Profile;
import com.ebac.streaming.profile.ProfileRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AccountRepository accountRepository, ProfileRepository profileRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResult register(String email, String rawPassword, String rawPin, String defaultProfileName) {
        if (accountRepository.existsByEmail(email)) {
            throw new EmailAlreadyInUseException("This email is already registered.");
        }

        Account account = Account.register(
                email, passwordEncoder.encode(rawPassword), passwordEncoder.encode(rawPin));
        accountRepository.save(account);

        Profile ownerProfile = Profile.createOwner(account, defaultProfileName);
        profileRepository.save(ownerProfile);

        String token = jwtService.generateToken(account.getId());
        return new AuthResult(token, account.getId(), ownerProfile.getId());
    }

    public AuthResult login(String email, String rawPassword) {
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password."));

        if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        String token = jwtService.generateToken(account.getId());
        return new AuthResult(token, account.getId(), null);
    }
}