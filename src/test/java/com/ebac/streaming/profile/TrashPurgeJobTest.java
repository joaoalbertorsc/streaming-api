package com.ebac.streaming.profile;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.review.ReviewRepository;
import com.ebac.streaming.watch.WatchHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrashPurgeJobTest {

    @Mock private ProfileRepository profileRepository;
    @Mock private WatchHistoryRepository watchHistoryRepository;
    @Mock private ReviewRepository reviewRepository;

    private TrashPurgeJob trashPurgeJob;
    private Account account;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        trashPurgeJob = new TrashPurgeJob(profileRepository, watchHistoryRepository, reviewRepository);
        account = Account.register("user@example.com", "hash", "pin");
    }

    @Test
    void purgesProfilesTrashedOverThirtyDaysAgo() {
        Profile oldTrashed = Profile.create(account, "Old", AgeCategory.CHILD);
        oldTrashed.moveToTrash();
        when(profileRepository.findByDeletedAtIsNotNullAndDeletedAtBefore(any(Instant.class)))
                .thenReturn(List.of(oldTrashed));

        trashPurgeJob.purgeExpiredProfiles();

        verify(profileRepository).delete(oldTrashed);
    }

    @Test
    void leavesRecentlyTrashedProfilesUntouchedBecauseQueryExcludesThem() {
        when(profileRepository.findByDeletedAtIsNotNullAndDeletedAtBefore(any(Instant.class)))
                .thenReturn(List.of());

        trashPurgeJob.purgeExpiredProfiles();

        verify(profileRepository, never()).delete(any());
    }

    @Test
    void cascadesDeletionOfHistoryAndReviewsBeforeDeletingTheProfile() {
        Profile oldTrashed = Profile.create(account, "Old", AgeCategory.CHILD);
        oldTrashed.moveToTrash();
        when(profileRepository.findByDeletedAtIsNotNullAndDeletedAtBefore(any(Instant.class)))
                .thenReturn(List.of(oldTrashed));

        trashPurgeJob.purgeExpiredProfiles();

        verify(watchHistoryRepository).deleteByProfile(oldTrashed);
        verify(reviewRepository).deleteByProfile(oldTrashed);
        verify(profileRepository).delete(oldTrashed);
    }

    @Test
    void usesACutoffOfThirtyDaysAgo() {
        when(profileRepository.findByDeletedAtIsNotNullAndDeletedAtBefore(any(Instant.class)))
                .thenReturn(List.of());

        trashPurgeJob.purgeExpiredProfiles();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(profileRepository).findByDeletedAtIsNotNullAndDeletedAtBefore(cutoffCaptor.capture());

        Instant cutoff = cutoffCaptor.getValue();
        Instant expectedApprox = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        long diffSeconds = Math.abs(cutoff.getEpochSecond() - expectedApprox.getEpochSecond());
        org.assertj.core.api.Assertions.assertThat(diffSeconds).isLessThan(5);
    }
}