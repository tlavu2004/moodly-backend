package com.tlavu.moodly.modules.dashboard.service;

import com.tlavu.moodly.modules.dashboard.controller.DashboardResponse;
import com.tlavu.moodly.modules.entries.dto.response.DailyEntryResponse;
import com.tlavu.moodly.modules.entries.service.implementation.EntryReadService;
import com.tlavu.moodly.modules.habits.dto.response.HabitResponse;
import com.tlavu.moodly.modules.habits.service.implementation.HabitService;
import com.tlavu.moodly.modules.habits.service.view.HabitView;
import com.tlavu.moodly.modules.stats.domain.MoodTrendPeriod;
import com.tlavu.moodly.modules.stats.service.StatsService;
import java.time.LocalDate;
import java.util.List;
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
		List<HabitView> habits = habitService.findActive(userId);
		var todayEntry = entryReadService.findByDate(userId, today).orElse(null);
		var completedHabitCount = todayEntry == null ? 0 : (int) todayEntry.habits().stream()
				.filter(log -> log.done() && habits.stream().anyMatch(habit -> habit.id().equals(log.habitId())))
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
		var habitIds = habits.stream().map(HabitView::id).toList();
		var bestCurrentStreak = statsService.calculateBestCurrentStreak(userId, habitIds, today);

		return new DashboardResponse(
				todayEntry == null ? null : DailyEntryResponse.from(todayEntry),
				habits.stream().map(HabitResponse::from).toList(),
				completedHabitCount,
				totalHabitCount,
				completionRatio,
				weeklyMood,
				bestCurrentStreak
		);
	}

}
