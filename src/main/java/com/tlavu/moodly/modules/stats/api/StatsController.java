package com.tlavu.moodly.modules.stats.api;

import com.tlavu.moodly.modules.stats.application.StatsService;
import com.tlavu.moodly.modules.auth.application.CurrentUser;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.time.LocalDate;
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
	@Operation(summary = "Get current-week mood trend", description = "Returns daily mood buckets for the current Monday-through-Sunday week in the Asia/Ho_Chi_Minh timezone. Only `period=week` is supported.")
	public ApiResponse<List<MoodTrendResponse>> moodTrend(
			@Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = MoodTrendPeriod.class))
			@RequestParam(defaultValue = "week") String period
	) {
		var requestedPeriod = MoodTrendPeriod.fromValue(period);
		var today = LocalDate.now(MoodTrendPeriod.TIME_ZONE);
		return ApiResponse.success(statsService.findMoodTrend(currentUser.id(), requestedPeriod, today));
	}

	@GetMapping("/most-missed-habits")
	@Operation(summary = "Get most missed habits", description = "Returns the authenticated user's habits ranked by missed completions.")
	public ApiResponse<List<MostMissedHabitResponse>> mostMissedHabits() {
		return ApiResponse.success(statsService.findMostMissedHabits(currentUser.id()));
	}
}
