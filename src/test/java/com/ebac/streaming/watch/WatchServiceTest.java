package com.ebac.streaming.watch;

import com.ebac.streaming.account.Account;
import com.ebac.streaming.exception.ContentNotAllowedException;
import com.ebac.streaming.exception.InvalidPinException;
import com.ebac.streaming.exception.ProfileNotFoundException;
import com.ebac.streaming.movie.ContentRating;
import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.movie.MovieService;
import com.ebac.streaming.plan.Plan;
import com.ebac.streaming.profile.AgeCategory;
import com.ebac.streaming.profile.Profile;
import com.ebac.streaming.profile.ProfileAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WatchServiceTest {

    @Mock private ProfileAccessService profileAccessService;
    @Mock private MovieService movieService;
    @Mock private PinPolicy pinPolicy;
    @Mock private WatchHistoryRepository watchHistoryRepository;

    private WatchService watchService;
    private Account account;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        watchService = new WatchService(profileAccessService, movieService, pinPolicy, watchHistoryRepository);
        account = Account.register("user@example.com", "hash", "pinHash");
        account.changePlan(Plan.PREMIUM);
    }

    private Movie movie(Plan minimumPlan, ContentRating classification) {
        return new Movie("Title", "Genre", "Synopsis", 2020, "Cast", 100,
                classification, minimumPlan);
    }

    @Test
    void rejectsWhenProfileIsForeignOrTrashed() {
        UUID profileId = UUID.randomUUID();
        UUID movieId = UUID.randomUUID();
        when(profileAccessService.getActiveOwnedProfile(account, profileId))
                .thenThrow(new ProfileNotFoundException("Profile not found."));

        assertThatThrownBy(() -> watchService.watch(account, profileId, movieId, null))
                .isInstanceOf(ProfileNotFoundException.class);

        verifyNoInteractions(watchHistoryRepository);
    }

    @Test
    void rejectsWhenContentAccessIsBlockedAndWritesNoHistory() {
        Profile profile = Profile.create(account, "Kids", AgeCategory.CHILD);
        Movie movie = movie(Plan.BASIC, ContentRating.TEN);
        when(profileAccessService.getActiveOwnedProfile(account, profile.getId())).thenReturn(profile);
        when(movieService.getAllowed(movie.getId(), account.getPlan(), profile.getAgeCategory()))
                .thenThrow(new ContentNotAllowedException("blocked"));

        assertThatThrownBy(() -> watchService.watch(account, profile.getId(), movie.getId(), null))
                .isInstanceOf(ContentNotAllowedException.class);

        verifyNoInteractions(watchHistoryRepository);
    }

    @Test
    void rejectsSixteenRatedContentWithoutPinAndWritesNoHistory() {
        Profile profile = Profile.createOwner(account, "Main");
        Movie movie = movie(Plan.BASIC, ContentRating.SIXTEEN);
        when(profileAccessService.getActiveOwnedProfile(account, profile.getId())).thenReturn(profile);
        when(movieService.getAllowed(movie.getId(), account.getPlan(), profile.getAgeCategory()))
                .thenReturn(movie);

        assertThatThrownBy(() -> watchService.watch(account, profile.getId(), movie.getId(), null))
                .isInstanceOf(InvalidPinException.class);

        verifyNoInteractions(watchHistoryRepository);
    }

    @Test
    void rejectsEighteenRatedContentWithWrongPinAndWritesNoHistory() {
        Profile profile = Profile.createOwner(account, "Main");
        Movie movie = movie(Plan.BASIC, ContentRating.EIGHTEEN);
        when(profileAccessService.getActiveOwnedProfile(account, profile.getId())).thenReturn(profile);
        when(movieService.getAllowed(movie.getId(), account.getPlan(), profile.getAgeCategory()))
                .thenReturn(movie);
        doThrow(new InvalidPinException("Incorrect PIN."))
                .when(pinPolicy).validate("0000", account.getPinHash());

        assertThatThrownBy(() -> watchService.watch(account, profile.getId(), movie.getId(), "0000"))
                .isInstanceOf(InvalidPinException.class);

        verifyNoInteractions(watchHistoryRepository);
    }

    @Test
    void allowsSixteenRatedContentWithCorrectPinAndWritesHistory() {
        Profile profile = Profile.createOwner(account, "Main");
        Movie movie = movie(Plan.BASIC, ContentRating.SIXTEEN);
        when(profileAccessService.getActiveOwnedProfile(account, profile.getId())).thenReturn(profile);
        when(movieService.getAllowed(movie.getId(), account.getPlan(), profile.getAgeCategory()))
                .thenReturn(movie);
        doNothing().when(pinPolicy).validate("1234", account.getPinHash());

        WatchResult result = watchService.watch(account, profile.getId(), movie.getId(), "1234");

        assertThat(result.movieTitle()).isEqualTo("Title");
        ArgumentCaptor<WatchHistoryEntry> captor = ArgumentCaptor.forClass(WatchHistoryEntry.class);
        verify(watchHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getProfile()).isEqualTo(profile);
        assertThat(captor.getValue().getMovie()).isEqualTo(movie);
    }

    @Test
    void doesNotRequirePinForContentRatedFourteenOrBelow() {
        Profile profile = Profile.create(account, "Teen", AgeCategory.TEEN);
        Movie movie = movie(Plan.BASIC, ContentRating.FOURTEEN);
        when(profileAccessService.getActiveOwnedProfile(account, profile.getId())).thenReturn(profile);
        when(movieService.getAllowed(movie.getId(), account.getPlan(), profile.getAgeCategory()))
                .thenReturn(movie);

        WatchResult result = watchService.watch(account, profile.getId(), movie.getId(), null);

        assertThat(result.movieTitle()).isEqualTo("Title");
        verifyNoInteractions(pinPolicy);
        verify(watchHistoryRepository).save(any());
    }

    @Test
    void repeatWatchCreatesASecondSeparateHistoryEntry() {
        Profile profile = Profile.create(account, "Teen", AgeCategory.TEEN);
        Movie movie = movie(Plan.BASIC, ContentRating.FOURTEEN);
        when(profileAccessService.getActiveOwnedProfile(account, profile.getId())).thenReturn(profile);
        when(movieService.getAllowed(movie.getId(), account.getPlan(), profile.getAgeCategory()))
                .thenReturn(movie);

        watchService.watch(account, profile.getId(), movie.getId(), null);
        watchService.watch(account, profile.getId(), movie.getId(), null);

        verify(watchHistoryRepository, times(2)).save(any());
    }
}