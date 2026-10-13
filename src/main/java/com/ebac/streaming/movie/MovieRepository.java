package com.ebac.streaming.movie;

import com.ebac.streaming.plan.Plan;
import com.ebac.streaming.profile.AgeCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MovieRepository extends JpaRepository<Movie, UUID> {

    @Query("""
            SELECT m FROM Movie m
            WHERE m.minimumPlan IN :allowedPlans
            AND m.classification IN :allowedRatings
            """)
    List<Movie> findAllowedFor(@Param("allowedPlans") List<Plan> allowedPlans,
                               @Param("allowedRatings") List<ContentRating> allowedRatings);
}