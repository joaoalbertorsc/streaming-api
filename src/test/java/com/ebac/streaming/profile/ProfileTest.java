package com.ebac.streaming.profile;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.exception.IllegalProfileOperationException;
import org.junit.jupiter.api.Test;

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
}