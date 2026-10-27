package com.tlavu.moodly.modules.stats.repository.contract;

import com.tlavu.moodly.modules.stats.dto.response.MoodTrendResponse;
import com.tlavu.moodly.modules.stats.dto.response.MostMissedHabitResponse;
import java.time.LocalDate;
import java.util.List;

public interface StatsAggregationRepository {

	List<MoodTrendResponse> findMoodTrend(String userId, LocalDate from, LocalDate to);

	List<MostMissedHabitResponse> findMostMissedHabits(String userId);
}
