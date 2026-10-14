package com.ebac.streaming.profile;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.review.Review;
import com.ebac.streaming.review.ReviewRepository;
import com.ebac.streaming.support.PostgresTestSupport;
import com.ebac.streaming.watch.WatchHistoryEntry;
import com.ebac.streaming.watch.WatchHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Runs the purge job against real PostgreSQL tables, so foreign keys from history and reviews to
 * the profile are enforced exactly as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TrashPurgeJob.class)
class TrashPurgeJobPostgresTest extends PostgresTestSupport {

    private static final UUID SEEDED_MOVIE = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired private TestEntityManager entityManager;
    @Autowired private TrashPurgeJob trashPurgeJob;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private WatchHistoryRepository watchHistoryRepository;
    @Autowired private ReviewRepository reviewRepository;

    private Profile trashedProfileWithActivity(Account account, String name, int daysInTrash) {
        Movie movie = entityManager.find(Movie.class, SEEDED_MOVIE);
        Profile profile = Profile.create(account, name, AgeCategory.CHILD);
        entityManager.persist(profile);
        entityManager.persist(new WatchHistoryEntry(profile, movie));
        entityManager.persist(new Review(profile, movie, 4, null));
        profile.moveToTrash();
        entityManager.flush();

        entityManager.getEntityManager()
                .createNativeQuery("UPDATE profile SET deleted_at = :deletedAt WHERE id = :id")
                .setParameter("deletedAt", Instant.now().minus(daysInTrash, ChronoUnit.DAYS))
                .setParameter("id", profile.getId())
                .executeUpdate();
        return profile;
    }

    @Test
    void purgesExpiredProfileWithItsHistoryAndReviewsAndKeepsRecentOnes() {
        Account account = Account.register("purge@example.com", "hash", "pin");
        entityManager.persist(account);
        Profile expired = trashedProfileWithActivity(account, "Expired", 31);
        Profile recent = trashedProfileWithActivity(account, "Recent", 5);
        entityManager.clear();

        trashPurgeJob.purgeExpiredProfiles();
        entityManager.flush();
        entityManager.clear();

        assertThat(profileRepository.findById(expired.getId())).isEmpty();
        assertThat(watchHistoryRepository.findByProfileOrderByWatchedAtDesc(expired)).isEmpty();
        assertThat(reviewRepository.findAll()).extracting(review -> review.getProfile().getId())
                .doesNotContain(expired.getId())
                .contains(recent.getId());

        assertThat(profileRepository.findById(recent.getId())).isPresent();
        assertThat(watchHistoryRepository.findByProfileOrderByWatchedAtDesc(recent)).hasSize(1);
    }
}
