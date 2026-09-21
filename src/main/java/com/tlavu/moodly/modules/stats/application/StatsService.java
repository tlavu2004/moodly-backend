package com.tlavu.moodly.modules.stats.application;

import com.tlavu.moodly.modules.entries.application.EntryReadService;
import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.stats.api.HabitStreakResponse;
import com.tlavu.moodly.modules.stats.api.MoodTrendResponse;
import com.tlavu.moodly.modules.stats.api.MoodTrendPeriod;
import com.tlavu.moodly.modules.stats.api.MostMissedHabitResponse;
import com.tlavu.moodly.modules.stats.infrastructure.StatsAggregationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
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

	public List<MoodTrendResponse> findMoodTrend(String userId, MoodTrendPeriod period, LocalDate today) {
		return statsAggregationRepository.findMoodTrend(
				userId,
				period.startDate(today),
				period.endDate(today)
		);
	}

	public List<MostMissedHabitResponse> findMostMissedHabits(String userId) {
		return statsAggregationRepository.findMostMissedHabits(userId);
	}

	public HabitStreakResponse calculateCurrentStreak(String userId, String habitId, LocalDate today) {
		var entries = entryReadService.findOnOrBefore(userId, today);
		return new HabitStreakResponse(habitId, calculateCurrentStreaks(entries, List.of(habitId), today).get(habitId));
	}

	public int calculateBestCurrentStreak(String userId, List<String> habitIds, LocalDate today) {
		if (habitIds.isEmpty()) {
			return 0;
		}
		var streaks = calculateCurrentStreaks(entryReadService.findOnOrBefore(userId, today), habitIds, today);
		return streaks.values().stream().mapToInt(Integer::intValue).max().orElse(0);
	}

	private java.util.Map<String, Integer> calculateCurrentStreaks(
			List<DailyEntry> entries,
			List<String> habitIds,
			LocalDate today
	) {
		var streaks = new HashMap<String, Integer>();
		var activeHabitIds = new HashSet<>(habitIds);
		activeHabitIds.forEach(habitId -> streaks.put(habitId, 0));
		var expectedDate = today;
		for (var entry : entries) {
			if (entry.getDate().isBefore(expectedDate)) {
				break;
			}
			var completedHabitIds = entry.getHabits().stream()
					.filter(DailyEntry.HabitLog::isDone)
					.map(DailyEntry.HabitLog::getHabitId)
					.collect(java.util.stream.Collectors.toSet());
			var iterator = activeHabitIds.iterator();
			while (iterator.hasNext()) {
				var habitId = iterator.next();
				if (completedHabitIds.contains(habitId)) {
					streaks.computeIfPresent(habitId, (ignored, streak) -> streak + 1);
				} else {
					iterator.remove();
				}
			}
			if (activeHabitIds.isEmpty()) {
				break;
			}
			expectedDate = expectedDate.minusDays(1);
		}
		return streaks;
	}
}
