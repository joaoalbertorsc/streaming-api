package com.ebac.streaming.watch;

import com.ebac.streaming.profile.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HistoryService {

    private final WatchHistoryRepository watchHistoryRepository;

    public HistoryService(WatchHistoryRepository watchHistoryRepository) {
        this.watchHistoryRepository = watchHistoryRepository;
    }

    public List<WatchHistoryEntry> getHistory(Profile profile) {
        return watchHistoryRepository.findByProfileOrderByWatchedAtDesc(profile);
    }
}