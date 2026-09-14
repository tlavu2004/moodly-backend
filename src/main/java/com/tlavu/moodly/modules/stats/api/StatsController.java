package com.tlavu.moodly.modules.stats.api;

import com.tlavu.moodly.modules.stats.application.StatsService;
import com.tlavu.moodly.modules.auth.application.CurrentUser;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
@Tag(name = "Statistics", description = "Mood and habit statistics for the authenticated user")
public class StatsController {

	private final StatsService statsService;
	private final CurrentUser currentUser;

	public StatsController(StatsService statsService, CurrentUser currentUser) {
		this.statsService = statsService;
		this.currentUser = currentUser;
	}

	@GetMapping("/mood-trend")
	@Operation(summary = "Get weekly mood trend", description = "Returns the authenticated user's mood trend for the current week. Only `period=week` is currently supported.")
	public ApiResponse<List<MoodTrendResponse>> moodTrend(
			@RequestParam(defaultValue = "week") String period
	) {
		if (!"week".equals(period)) {
			throw new IllegalArgumentException("Only period=week is supported.");
		}
		return ApiResponse.success(statsService.findWeeklyMoodTrend(currentUser.id()));
	}

	@GetMapping("/most-missed-habits")
	@Operation(summary = "Get most missed habits", description = "Returns the authenticated user's habits ranked by missed completions.")
	public ApiResponse<List<MostMissedHabitResponse>> mostMissedHabits() {
		return ApiResponse.success(statsService.findMostMissedHabits(currentUser.id()));
	}
}
