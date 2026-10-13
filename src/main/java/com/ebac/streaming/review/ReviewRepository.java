package com.ebac.streaming.review;

import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.profile.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByProfileAndMovie(Profile profile, Movie movie);

    List<Review> findByProfile(Profile profile);

    void deleteByProfile(Profile profile);
}