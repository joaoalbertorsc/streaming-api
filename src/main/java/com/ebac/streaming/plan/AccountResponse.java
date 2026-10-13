package com.ebac.streaming.plan;

import com.ebac.streaming.account.Account;

import java.util.UUID;

public record AccountResponse(UUID id, String email, Plan plan) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getEmail(), account.getPlan());
    }
}