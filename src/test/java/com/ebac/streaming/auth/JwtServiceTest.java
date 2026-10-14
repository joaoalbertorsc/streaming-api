package com.ebac.streaming.auth;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "TEST_SECRET_TEST_SECRET_TEST_SECRET_32B";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(SECRET, 60);
        jwtService = new JwtService(properties);
    }

    @Test
    void generatedTokenResolvesBackToTheSameAccountId() {
        UUID accountId = UUID.randomUUID();

        String token = jwtService.generateToken(accountId);
        UUID extracted = jwtService.extractAccountId(token);

        assertThat(extracted).isEqualTo(accountId);
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        JwtService otherIssuer = new JwtService(
                new JwtProperties("ANOTHER_SECRET_ANOTHER_SECRET_ANOTHER_32B", 60));
        String foreignToken = otherIssuer.generateToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtService.extractAccountId(foreignToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenWhosePayloadWasTampered() {
        String token = jwtService.generateToken(UUID.randomUUID());
        String[] parts = token.split("\\.");
        // Swap the payload for another account's, keeping the original signature.
        String otherPayload = jwtService.generateToken(UUID.randomUUID()).split("\\.")[1];
        String tampered = parts[0] + "." + otherPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtService.extractAccountId(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService alreadyExpiring = new JwtService(new JwtProperties(SECRET, -1));
        String expiredToken = alreadyExpiring.generateToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtService.extractAccountId(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
