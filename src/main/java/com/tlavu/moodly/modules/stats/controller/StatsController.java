package com.tlavu.moodly.modules.stats.controller;

import com.tlavu.moodly.modules.auth.security.context.CurrentUser;
import com.tlavu.moodly.modules.stats.domain.MoodTrendPeriod;
import com.tlavu.moodly.modules.stats.dto.response.MoodTrendResponse;
import com.tlavu.moodly.modules.stats.dto.response.MostMissedHabitResponse;
import com.tlavu.moodly.modules.stats.service.implementation.StatsService;
import com.tlavu.moodly.modules.stats.service.input.FindMoodTrendInput;
import com.tlavu.moodly.modules.stats.service.view.MoodTrendView;
import com.tlavu.moodly.modules.stats.service.view.MostMissedHabitView;
import com.tlavu.moodly.shared.controller.dto.response.ApiResponse;
import com.tlavu.moodly.shared.time.MoodlyTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
@Tag(name = "Statistics", description = "Mood and habit statistics for the authenticated user")
@RequiredArgsConstructor
public class StatsController {
	private final StatsService statsService;
	private final CurrentUser currentUser;
	private final Clock clock;

	@GetMapping("/mood-trend")
	@Operation(
			summary = "Get current-week mood trend",
			description = "Returns daily mood buckets for the current Monday-through-Sunday week in the Asia/Ho_Chi_Minh timezone. Only `period=week` is supported."
	)
	public ApiResponse<List<MoodTrendResponse>> moodTrend(
			@Parameter(schema = @Schema(implementation = MoodTrendPeriod.class))
			@RequestParam(defaultValue = "week") String period
	) {
		MoodTrendPeriod requestedPeriod = MoodTrendPeriod.fromValue(period);
		LocalDate today = MoodlyTime.today(clock);
		FindMoodTrendInput input = new FindMoodTrendInput(requestedPeriod, today);

		List<MoodTrendView> trend = statsService.findMoodTrend(currentUser.id(), input);

		return ApiResponse.success(trend.stream().map(MoodTrendResponse::from).toList());
	}

	@GetMapping("/most-missed-habits")
	@Operation(
			summary = "Get most missed habits",
			description = "Returns the authenticated user's habits ranked by missed completions."
	)
	public ApiResponse<List<MostMissedHabitResponse>> mostMissedHabits() {
		List<MostMissedHabitView> missedHabits = statsService.findMostMissedHabits(currentUser.id());

		return ApiResponse.success(
				missedHabits.stream()
						.map(MostMissedHabitResponse::from)
						.toList()
		);
	}
}
