package com.tlavu.moodly.modules.stats.api;

import com.tlavu.moodly.modules.stats.application.StatsService;
import com.tlavu.moodly.modules.auth.application.CurrentUser;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
public class StatsController {

	private final StatsService statsService;
	private final CurrentUser currentUser;

	public StatsController(StatsService statsService, CurrentUser currentUser) {
		this.statsService = statsService;
		this.currentUser = currentUser;
	}

	@GetMapping("/mood-trend")
	public ApiResponse<List<MoodTrendResponse>> moodTrend(
			@RequestParam(defaultValue = "week") String period
	) {
		if (!"week".equals(period)) {
			throw new IllegalArgumentException("Only period=week is supported.");
		}
		return ApiResponse.success(statsService.findWeeklyMoodTrend(currentUser.id()));
	}

	@GetMapping("/most-missed-habits")
	public ApiResponse<List<MostMissedHabitResponse>> mostMissedHabits() {
		return ApiResponse.success(statsService.findMostMissedHabits(currentUser.id()));
	}
}
