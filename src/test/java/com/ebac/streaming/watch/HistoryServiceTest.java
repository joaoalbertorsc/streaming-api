package com.ebac.streaming.watch;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.movie.ContentRating;
import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.plan.Plan;
import com.ebac.streaming.profile.AgeCategory;
import com.ebac.streaming.profile.Profile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoryServiceTest {

    @Mock private WatchHistoryRepository watchHistoryRepository;

    private HistoryService historyService;
    private Account account;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        historyService = new HistoryService(watchHistoryRepository);
        account = Account.register("user@example.com", "hash", "pin");
    }

    private Movie movie(String title) {
        return new Movie(title, "Genre", "Synopsis", 2020, "Cast", 100,
                ContentRating.L, Plan.BASIC);
    }

    @Test
    void returnsEntriesOrderedByWatchedAtDescForTheGivenProfile() {
        Profile profile = Profile.create(account, "Kids", AgeCategory.CHILD);
        WatchHistoryEntry mostRecent = new WatchHistoryEntry(profile, movie("Movie Y"));
        WatchHistoryEntry older = new WatchHistoryEntry(profile, movie("Movie X"));
        when(watchHistoryRepository.findByProfileOrderByWatchedAtDesc(profile))
                .thenReturn(List.of(mostRecent, older));

        List<WatchHistoryEntry> result = historyService.getHistory(profile);

        assertThat(result).containsExactly(mostRecent, older);
    }

    @Test
    void queriesOnlyTheGivenProfileNotOtherProfilesOnTheSameAccount() {
        Profile profileA = Profile.create(account, "Profile A", AgeCategory.ADULT);
        Profile profileB = Profile.create(account, "Profile B", AgeCategory.ADULT);
        when(watchHistoryRepository.findByProfileOrderByWatchedAtDesc(profileA))
                .thenReturn(List.of(new WatchHistoryEntry(profileA, movie("A's movie"))));

        historyService.getHistory(profileA);

        verify(watchHistoryRepository).findByProfileOrderByWatchedAtDesc(profileA);
        verify(watchHistoryRepository, org.mockito.Mockito.never())
                .findByProfileOrderByWatchedAtDesc(profileB);
    }
}