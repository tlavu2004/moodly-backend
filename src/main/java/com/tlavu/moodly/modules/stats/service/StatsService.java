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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatsService {

	private final EntryReadService entryReadService;
	private final StatsAggregationRepository statsAggregationRepository;

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
		List<DailyEntryView> entries = entryReadService.findOnOrBefore(userId, today);
		return new HabitStreakResponse(habitId, calculateCurrentStreaks(entries, List.of(habitId), today).get(habitId));
	}

	public int calculateBestCurrentStreak(String userId, List<String> habitIds, LocalDate today) {
		if (habitIds.isEmpty()) {
			return 0;
		}
		Map<String, Integer> streaks = calculateCurrentStreaks(entryReadService.findOnOrBefore(userId, today), habitIds, today);
		return streaks.values().stream().mapToInt(Integer::intValue).max().orElse(0);
	}

	private Map<String, Integer> calculateCurrentStreaks(
			List<DailyEntryView> entries,
			List<String> habitIds,
			LocalDate today
	) {
		Map<String, Integer> streaks = new HashMap<>();
		Set<String> activeHabitIds = new HashSet<>(habitIds);
		activeHabitIds.forEach(habitId -> streaks.put(habitId, 0));
		LocalDate expectedDate = today;
		for (DailyEntryView entry : entries) {
			if (entry.date().isBefore(expectedDate)) {
				break;
			}
			Set<String> completedHabitIds = entry.habits().stream()
					.filter(HabitLogView::done)
					.map(HabitLogView::habitId)
					.collect(java.util.stream.Collectors.toSet());
			Iterator<String> iterator = activeHabitIds.iterator();
			while (iterator.hasNext()) {
				String habitId = iterator.next();
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
