package com.ebac.streaming.profile;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.exception.IllegalProfileOperationException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileTest {

    private Account anAccount() {
        return Account.register("user@example.com", "hashedPassword", "hashedPin");
    }

    @Test
    void createOwnerProducesAnActiveAdultOwnerProfile() {
        Account account = anAccount();

        Profile owner = Profile.createOwner(account, "Main");

        assertThat(owner.isOwner()).isTrue();
        assertThat(owner.getAgeCategory()).isEqualTo(AgeCategory.ADULT);
        assertThat(owner.isActive()).isTrue();
    }

    @Test
    void moveToTrashSetsDeletedAtForNonOwnerProfile() {
        Profile profile = Profile.create(anAccount(), "Kids", AgeCategory.CHILD);

        profile.moveToTrash();

        assertThat(profile.isActive()).isFalse();
    }

    @Test
    void moveToTrashThrowsForOwnerProfile() {
        Profile owner = Profile.createOwner(anAccount(), "Main");

        assertThatThrownBy(owner::moveToTrash)
                .isInstanceOf(IllegalProfileOperationException.class);
    }

    @Test
    void restoreClearsDeletedAt() {
        Profile profile = Profile.create(anAccount(), "Kids", AgeCategory.CHILD);
        profile.moveToTrash();

        profile.restore();

        assertThat(profile.isActive()).isTrue();
    }

    @Test
    void trashIsExpiredOnlyAfterTheRetentionPeriod() {
        Account account = Account.register("user@example.com", "hash", "pin");
        Profile profile = Profile.create(account, "Kids", AgeCategory.CHILD);
        profile.moveToTrash();
        assertThat(profile.isTrashExpired()).isFalse();

        ReflectionTestUtils.setField(profile, "deletedAt",
                Instant.now().minus(Profile.TRASH_RETENTION).minusSeconds(60));
        assertThat(profile.isTrashExpired()).isTrue();
    }

    @Test
    void activeProfileIsNeverTrashExpired() {
        Account account = Account.register("user@example.com", "hash", "pin");

        assertThat(Profile.create(account, "Kids", AgeCategory.CHILD).isTrashExpired()).isFalse();
    }
}
