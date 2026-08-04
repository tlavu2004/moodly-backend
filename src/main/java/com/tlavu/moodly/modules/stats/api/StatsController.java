package com.tlavu.moodly.modules.stats.api;

import com.tlavu.moodly.modules.stats.application.StatsService;
import com.tlavu.moodly.shared.api.dto.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
public class StatsController {

	private final StatsService statsService;

	public StatsController(StatsService statsService) {
		this.statsService = statsService;
	}

	@GetMapping("/mood-trend")
	public ApiResponse<List<MoodTrendResponse>> moodTrend(
			@RequestHeader("X-User-Id") String userId,
			@RequestParam(defaultValue = "week") String period
	) {
		if (!"week".equals(period)) {
			throw new IllegalArgumentException("Only period=week is supported.");
		}
		return ApiResponse.success(statsService.findWeeklyMoodTrend(userId));
	}

	@GetMapping("/most-missed-habits")
	public ApiResponse<List<MostMissedHabitResponse>> mostMissedHabits(@RequestHeader("X-User-Id") String userId) {
		return ApiResponse.success(statsService.findMostMissedHabits(userId));
	}
}
