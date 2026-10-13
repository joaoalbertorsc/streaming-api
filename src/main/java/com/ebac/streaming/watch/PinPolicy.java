package com.ebac.streaming.watch;

import com.ebac.streaming.exception.InvalidPinException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PinPolicy {

    private final PasswordEncoder passwordEncoder;

    public PinPolicy(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public void validate(String rawPin, String pinHash) {
        if (!passwordEncoder.matches(rawPin, pinHash)) {
            throw new InvalidPinException("Incorrect PIN.");
        }
    }
}