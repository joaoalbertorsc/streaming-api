package com.ebac.streaming.review;

import com.ebac.streaming.movie.Movie;
import com.ebac.streaming.profile.Profile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(nullable = false)
    private int stars;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Review(Profile profile, Movie movie, int stars, String comment) {
        this.id = UUID.randomUUID();
        this.profile = profile;
        this.movie = movie;
        setStars(stars);
        this.comment = comment;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(int stars, String comment) {
        setStars(stars);
        this.comment = comment;
        this.updatedAt = Instant.now();
    }

    private void setStars(int stars) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Stars must be between 1 and 5.");
        }
        this.stars = stars;
    }
}