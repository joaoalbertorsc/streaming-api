package com.ebac.streaming.profile;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.exception.IllegalProfileOperationException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profile")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile {

    /** How long a trashed profile can still be listed and restored before it is purged. */
    public static final Duration TRASH_RETENTION = Duration.ofDays(30);

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_category", nullable = false)
    private AgeCategory ageCategory;

    @Column(nullable = false)
    private boolean owner;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Profile(Account account, String name, AgeCategory ageCategory, boolean owner) {
        this.id = UUID.randomUUID();
        this.account = account;
        this.name = name;
        this.ageCategory = ageCategory;
        this.owner = owner;
        this.createdAt = Instant.now();
    }

    public static Profile createOwner(Account account, String name) {
        return new Profile(account, name, AgeCategory.ADULT, true);
    }

    public static Profile create(Account account, String name, AgeCategory ageCategory) {
        return new Profile(account, name, ageCategory, false);
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    public boolean isTrashExpired() {
        return deletedAt != null && deletedAt.isBefore(Instant.now().minus(TRASH_RETENTION));
    }

    public void moveToTrash() {
        if (owner) {
            throw new IllegalProfileOperationException("The owner profile cannot be deleted.");
        }
        this.deletedAt = Instant.now();
    }

    public void restore() {
        this.deletedAt = null;
    }
}