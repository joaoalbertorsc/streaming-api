package com.ebac.streaming.account;

import com.ebac.streaming.plan.Plan;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "pin_hash", nullable = false)
    private String pinHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Account(String email, String passwordHash, String pinHash) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.pinHash = pinHash;
        this.plan = Plan.BASIC;
        this.createdAt = Instant.now();
    }

    public static Account register(String email, String passwordHash, String pinHash) {
        return new Account(email, passwordHash, pinHash);
    }

    public void changePlan(Plan newPlan) {
        this.plan = newPlan;
    }
}