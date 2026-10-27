package com.tlavu.moodly.modules.stats.repository.contract;

import com.tlavu.moodly.modules.stats.repository.projection.MoodTrendProjection;
import com.tlavu.moodly.modules.stats.repository.projection.MostMissedHabitProjection;
import java.time.LocalDate;
import java.util.List;

public interface StatsAggregationRepository {

	List<MoodTrendProjection> findMoodTrend(String userId, LocalDate from, LocalDate to);

	List<MostMissedHabitProjection> findMostMissedHabits(String userId);
}
