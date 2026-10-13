package com.ebac.streaming.watch;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.movie.ContentRating;
import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.plan.Plan;
import com.ebac.streaming.profile.AgeCategory;
import com.ebac.streaming.profile.Profile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@ActiveProfiles("test")
class WatchHistoryRepositoryTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private WatchHistoryRepository watchHistoryRepository;

    private Account persistAccount() {
        Account account = Account.register("user@example.com", "hash", "pin");
        entityManager.persist(account);
        return account;
    }

    private Movie persistMovie(String title) {
        Movie movie = new Movie(title, "Genre", "Synopsis", 2020, "Cast", 100,
                ContentRating.L, Plan.BASIC);
        entityManager.persist(movie);
        return movie;
    }

    @Test
    void returnsEntriesOrderedByWatchedAtDescForTheGivenProfile() throws InterruptedException {
        Account account = persistAccount();
        Profile profile = Profile.create(account, "Kids", AgeCategory.CHILD);
        entityManager.persist(profile);
        Movie movieX = persistMovie("Movie X");
        Movie movieY = persistMovie("Movie Y");

        WatchHistoryEntry older = new WatchHistoryEntry(profile, movieX);
        entityManager.persist(older);
        Thread.sleep(10); // ensure a distinct, later timestamp for the second entry
        WatchHistoryEntry mostRecent = new WatchHistoryEntry(profile, movieY);
        entityManager.persist(mostRecent);
        entityManager.flush();

        List<WatchHistoryEntry> result = watchHistoryRepository.findByProfileOrderByWatchedAtDesc(profile);

        assertThat(result).containsExactly(mostRecent, older);
    }

    @Test
    void doesNotReturnEntriesFromOtherProfiles() {
        Account account = persistAccount();
        Profile profileA = Profile.create(account, "Profile A", AgeCategory.ADULT);
        Profile profileB = Profile.create(account, "Profile B", AgeCategory.ADULT);
        entityManager.persist(profileA);
        entityManager.persist(profileB);
        Movie movie = persistMovie("Shared Movie");

        WatchHistoryEntry entryForA = new WatchHistoryEntry(profileA, movie);
        entityManager.persist(entryForA);
        entityManager.flush();

        List<WatchHistoryEntry> result = watchHistoryRepository.findByProfileOrderByWatchedAtDesc(profileB);

        assertThat(result).isEmpty();
    }
}