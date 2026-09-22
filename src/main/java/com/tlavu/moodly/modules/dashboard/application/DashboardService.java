package com.tlavu.moodly.modules.dashboard.application;

import com.tlavu.moodly.modules.dashboard.api.DashboardResponse;
import com.tlavu.moodly.modules.entries.application.EntryReadService;
import com.tlavu.moodly.modules.habits.application.HabitService;
import com.tlavu.moodly.modules.stats.api.MoodTrendPeriod;
import com.tlavu.moodly.modules.stats.application.StatsService;
import java.time.LocalDate;
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

	public DashboardResponse getDashboard(String userId, LocalDate today) {
		var habits = habitService.findActive(userId);
		var todayEntry = entryReadService.findByDate(userId, today).orElse(null);
		var completedHabitCount = todayEntry == null ? 0 : (int) todayEntry.getHabits().stream()
				.filter(log -> log.isDone() && habits.stream().anyMatch(habit -> habit.getId().equals(log.getHabitId())))
				.count();
		var totalHabitCount = habits.size();
		var completionRatio = totalHabitCount == 0 ? 0.0 : (double) completedHabitCount / totalHabitCount;
		var moodTrend = statsService.findMoodTrend(userId, MoodTrendPeriod.WEEK, today);
		var moodEntryCount = moodTrend.stream().mapToLong(point -> point.entryCount()).sum();
		var moodScoreTotal = moodTrend.stream()
				.mapToDouble(point -> point.averageScore() * point.entryCount())
				.sum();
		var weeklyMood = new DashboardResponse.WeeklyMoodSummary(
				moodEntryCount == 0 ? null : moodScoreTotal / moodEntryCount,
				moodEntryCount
		);
		var habitIds = habits.stream().map(habit -> habit.getId()).toList();
		var bestCurrentStreak = statsService.calculateBestCurrentStreak(userId, habitIds, today);

		return new DashboardResponse(
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
