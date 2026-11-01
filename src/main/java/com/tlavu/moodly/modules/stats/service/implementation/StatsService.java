package com.tlavu.moodly.modules.stats.service.implementation;

import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
import com.tlavu.moodly.modules.entries.service.view.HabitLogView;
import com.tlavu.moodly.modules.entries.service.implementation.EntryReadService;
import com.tlavu.moodly.modules.stats.service.input.CalculateBestCurrentStreakInput;
import com.tlavu.moodly.modules.stats.service.input.CalculateCurrentStreakInput;
import com.tlavu.moodly.modules.stats.service.input.FindMoodTrendInput;
import com.tlavu.moodly.modules.stats.service.view.HabitStreakView;
import com.tlavu.moodly.modules.stats.service.view.MoodTrendView;
import com.tlavu.moodly.modules.stats.service.view.MostMissedHabitView;
import com.tlavu.moodly.modules.stats.repository.contract.StatsAggregationRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatsService {
	private final EntryReadService entryReadService;
	private final StatsAggregationRepository statsAggregationRepository;

	public List<MoodTrendView> findMoodTrend(String userId, FindMoodTrendInput input) {
		LocalDate from = input.period().startDate(input.today());
		LocalDate to = input.period().endDate(input.today());

		return statsAggregationRepository.findMoodTrend(userId, from, to).stream()
				.map(MoodTrendView::from)
				.toList();
	}

	public List<MostMissedHabitView> findMostMissedHabits(String userId) {
		return statsAggregationRepository.findMostMissedHabits(userId).stream()
				.map(MostMissedHabitView::from)
				.toList();
	}

	public HabitStreakView calculateCurrentStreak(
			String userId,
			CalculateCurrentStreakInput input
	) {
		List<DailyEntryView> entries = entryReadService.findOnOrBefore(userId, input.today());
		String habitId = input.habitId();
		Map<String, Integer> streaks = calculateCurrentStreaks(
				entries,
				List.of(habitId),
				input.today()
		);

		return new HabitStreakView(habitId, streaks.get(habitId));
	}

	public int calculateBestCurrentStreak(
			String userId,
			CalculateBestCurrentStreakInput input
	) {
		if (input.habitIds().isEmpty()) {
			return 0;
		}

		Map<String, Integer> streaks = calculateCurrentStreaks(
				entryReadService.findOnOrBefore(userId, input.today()),
				input.habitIds(),
				input.today()
		);

		return streaks.values().stream()
				.mapToInt(Integer::intValue)
				.max()
				.orElse(0);
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
					.collect(Collectors.toSet());

			Iterator<String> iterator = activeHabitIds.iterator();

			while (iterator.hasNext()) {
				String habitId = iterator.next();

				if (completedHabitIds.contains(habitId)) {
					streaks.computeIfPresent(
							habitId,
							(ignored, streak) -> streak + 1
					);
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
