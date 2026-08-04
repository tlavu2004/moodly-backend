package com.tlavu.moodly.modules.stats.application;

import com.tlavu.moodly.modules.entries.application.EntryReadService;
import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.stats.api.HabitStreakResponse;
import com.tlavu.moodly.modules.stats.api.MoodTrendResponse;
import com.tlavu.moodly.modules.stats.api.MostMissedHabitResponse;
import com.tlavu.moodly.modules.stats.infrastructure.StatsAggregationRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StatsService {

	private final EntryReadService entryReadService;
	private final StatsAggregationRepository statsAggregationRepository;

	public StatsService(
			EntryReadService entryReadService,
			StatsAggregationRepository statsAggregationRepository
	) {
		this.entryReadService = entryReadService;
		this.statsAggregationRepository = statsAggregationRepository;
	}

	public List<MoodTrendResponse> findWeeklyMoodTrend(String userId) {
		return statsAggregationRepository.findWeeklyMoodTrend(userId);
	}

	public List<MostMissedHabitResponse> findMostMissedHabits(String userId) {
		return statsAggregationRepository.findMostMissedHabits(userId);
	}

	public HabitStreakResponse calculateCurrentStreak(String userId, String habitId, LocalDate today) {
		var expectedDate = today;
		var streak = 0;
		for (var entry : entryReadService.findOnOrBefore(userId, today)) {
			if (entry.getDate().isBefore(expectedDate)) {
				break;
			}
			var completed = entry.getHabits().stream()
					.filter(log -> log.getHabitId().equals(habitId))
					.anyMatch(DailyEntry.HabitLog::isDone);
			if (!completed) {
				break;
			}
			streak++;
			expectedDate = expectedDate.minusDays(1);
		}
		return new HabitStreakResponse(habitId, streak);
	}
}
