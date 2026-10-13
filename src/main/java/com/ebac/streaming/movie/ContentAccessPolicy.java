package com.ebac.streaming.movie;

import com.ebac.streaming.plan.Plan;
import com.ebac.streaming.profile.AgeCategory;
import org.springframework.stereotype.Component;

@Component
public class ContentAccessPolicy {

    public boolean isAllowed(Plan accountPlan, AgeCategory ageCategory, Movie movie) {
        return accountPlan.allows(movie.getMinimumPlan())
                && ageCategory.allows(movie.getClassification());
    }
}