package com.ebac.streaming.auth;

import java.util.UUID;

public record AuthResult(String token, UUID accountId, UUID defaultProfileId) {
}