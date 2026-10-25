package com.tlavu.moodly.modules.dashboard.controller;

import com.tlavu.moodly.modules.entries.dto.response.DailyEntryResponse;
import com.tlavu.moodly.modules.habits.dto.response.HabitResponse;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"activeHabits", "completedHabitCount", "totalHabitCount", "completionRatio", "weeklyMood", "bestCurrentStreak"})
public record DashboardResponse(
		@Schema(nullable = true) DailyEntryResponse todayEntry,
		List<HabitResponse> activeHabits,
		int completedHabitCount,
		int totalHabitCount,
		double completionRatio,
		WeeklyMoodSummary weeklyMood,
		int bestCurrentStreak
) {

	@Schema(requiredProperties = {"entryCount"})
	public record WeeklyMoodSummary(@Schema(nullable = true) Double averageScore, long entryCount) {
	}

}
