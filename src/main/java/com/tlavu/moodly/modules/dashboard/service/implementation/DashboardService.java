package com.tlavu.moodly.modules.dashboard.service.implementation;

import com.tlavu.moodly.modules.dashboard.service.view.DashboardView;
import com.tlavu.moodly.modules.dashboard.service.view.WeeklyMoodView;
import com.tlavu.moodly.modules.entries.service.implementation.EntryReadService;
import com.tlavu.moodly.modules.habits.service.implementation.HabitService;
import com.tlavu.moodly.modules.habits.service.view.HabitView;
import com.tlavu.moodly.modules.stats.domain.MoodTrendPeriod;
import com.tlavu.moodly.modules.stats.service.implementation.StatsService;
import com.tlavu.moodly.modules.stats.service.input.CalculateBestCurrentStreakInput;
import com.tlavu.moodly.modules.stats.service.input.FindMoodTrendInput;
import java.time.LocalDate;
import java.util.List;

import com.tlavu.moodly.modules.stats.service.view.MoodTrendView;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
	private final EntryReadService entryReadService;
	private final HabitService habitService;
	private final StatsService statsService;

	public DashboardService(
			EntryReadService entryReadService,
			HabitService habitService,
			StatsService statsService
	) {
		this.entryReadService = entryReadService;
		this.habitService = habitService;
		this.statsService = statsService;
	}

	public DashboardView getDashboard(String userId, LocalDate today) {
		List<HabitView> habits = habitService.findActive(userId);
		var todayEntry = entryReadService.findByDate(userId, today)
				.orElse(null);
		var completedHabitCount = todayEntry == null
				? 0
				: (int) todayEntry.habits().stream()
						.filter(
								log -> log.done() &&
								habits.stream()
										.anyMatch(habit -> habit.id().equals(log.habitId()))
						).count();
		var totalHabitCount = habits.size();
		var completionRatio = totalHabitCount == 0
				? 0.0
				: (double) completedHabitCount / totalHabitCount;
		var moodTrend = statsService.findMoodTrend(
				userId,
				new FindMoodTrendInput(MoodTrendPeriod.WEEK, today)
		);
		var moodEntryCount = moodTrend.stream()
				.mapToLong(MoodTrendView::entryCount)
				.sum();
		var moodScoreTotal = moodTrend.stream()
				.mapToDouble(point -> point.averageScore() * point.entryCount())
				.sum();
		var weeklyMood = new WeeklyMoodView(
				moodEntryCount == 0
						? null
						: moodScoreTotal / moodEntryCount,
				moodEntryCount
		);
		var habitIds = habits.stream()
				.map(HabitView::id)
				.toList();
		var bestCurrentStreak = statsService.calculateBestCurrentStreak(
				userId,
				new CalculateBestCurrentStreakInput(habitIds, today)
		);

		return new DashboardView(
				todayEntry,
				habits,
				completedHabitCount,
				totalHabitCount,
				completionRatio,
				weeklyMood,
				bestCurrentStreak
		);
	}
}
