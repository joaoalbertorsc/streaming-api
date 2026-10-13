package com.ebac.streaming.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "TEST_SECRET_TEST_SECRET_TEST_SECRET_32B", 60);
        jwtService = new JwtService(properties);
    }

    @Test
    void generatedTokenResolvesBackToTheSameAccountId() {
        UUID accountId = UUID.randomUUID();

        String token = jwtService.generateToken(accountId);
        UUID extracted = jwtService.extractAccountId(token);

        assertThat(extracted).isEqualTo(accountId);
    }
}