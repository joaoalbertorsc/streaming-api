package com.ebac.streaming.movie;

import com.ebac.streaming.plan.Plan;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "movie")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String genre;

    @Column(nullable = false, length = 1000)
    private String synopsis;

    @Column(name = "release_year", nullable = false)
    private Integer releaseYear;

    @Column(name = "cast_members", nullable = false, length = 1000)
    private String castMembers;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentRating classification;

    @Enumerated(EnumType.STRING)
    @Column(name = "minimum_plan", nullable = false)
    private Plan minimumPlan;

    public Movie(String title, String genre, String synopsis, Integer releaseYear,
                 String castMembers, Integer durationMinutes,
                 ContentRating classification, Plan minimumPlan) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.genre = genre;
        this.synopsis = synopsis;
        this.releaseYear = releaseYear;
        this.castMembers = castMembers;
        this.durationMinutes = durationMinutes;
        this.classification = classification;
        this.minimumPlan = minimumPlan;
    }
}