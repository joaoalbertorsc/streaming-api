package com.ebac.streaming.profile;

import com.ebac.streaming.review.ReviewRepository;
import com.ebac.streaming.watch.WatchHistoryRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class TrashPurgeJob {

    private final ProfileRepository profileRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final ReviewRepository reviewRepository;

    public TrashPurgeJob(ProfileRepository profileRepository, WatchHistoryRepository watchHistoryRepository,
                         ReviewRepository reviewRepository) {
        this.profileRepository = profileRepository;
        this.watchHistoryRepository = watchHistoryRepository;
        this.reviewRepository = reviewRepository;
    }

    @Scheduled(cron = "0 0 3 * * *") // every day at 03:00
    @Transactional
    public void purgeExpiredProfiles() {
        Instant cutoff = Instant.now().minus(Profile.TRASH_RETENTION);
        List<Profile> expiredProfiles = profileRepository.findByDeletedAtIsNotNullAndDeletedAtBefore(cutoff);

        for (Profile profile : expiredProfiles) {
            watchHistoryRepository.deleteByProfile(profile);
            reviewRepository.deleteByProfile(profile);
            profileRepository.delete(profile);
        }
    }
}