package com.ebac.streaming.auth;

import java.util.UUID;

public record AuthResponse(String token, UUID accountId, UUID defaultProfileId) {

    public static AuthResponse from(AuthResult result) {
        return new AuthResponse(result.token(), result.accountId(), result.defaultProfileId());
    }
}