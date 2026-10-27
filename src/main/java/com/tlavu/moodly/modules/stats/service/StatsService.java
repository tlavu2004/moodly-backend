package com.tlavu.moodly.modules.stats.service;

import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
import com.tlavu.moodly.modules.entries.service.view.HabitLogView;
import com.tlavu.moodly.modules.entries.service.implementation.EntryReadService;
import com.tlavu.moodly.modules.stats.domain.MoodTrendPeriod;
import com.tlavu.moodly.modules.stats.dto.response.HabitStreakResponse;
import com.tlavu.moodly.modules.stats.dto.response.MoodTrendResponse;
import com.tlavu.moodly.modules.stats.dto.response.MostMissedHabitResponse;
import com.tlavu.moodly.modules.stats.repository.contract.StatsAggregationRepository;
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
			List<DailyEntryView> entries,
			List<String> habitIds,
			LocalDate today
	) {
		var streaks = new HashMap<String, Integer>();
		var activeHabitIds = new HashSet<>(habitIds);
		activeHabitIds.forEach(habitId -> streaks.put(habitId, 0));
		var expectedDate = today;
		for (var entry : entries) {
			if (entry.date().isBefore(expectedDate)) {
				break;
			}
			var completedHabitIds = entry.habits().stream()
					.filter(HabitLogView::done)
					.map(HabitLogView::habitId)
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
