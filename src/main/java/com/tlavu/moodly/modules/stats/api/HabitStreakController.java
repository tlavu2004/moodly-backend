package com.tlavu.moodly.modules.stats.api;

import com.tlavu.moodly.modules.stats.application.StatsService;
import com.tlavu.moodly.modules.auth.application.CurrentUser;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.Clock;
import com.tlavu.moodly.shared.time.MoodlyTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/habits")
@Tag(name = "Habit statistics", description = "Statistics for the authenticated user's habits")
public class HabitStreakController {

	private final StatsService statsService;
	private final CurrentUser currentUser;
	private final Clock clock;

	public HabitStreakController(StatsService statsService, CurrentUser currentUser, Clock clock) {
		this.statsService = statsService;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@GetMapping("/{habitId}/streak")
	@Operation(summary = "Get a habit's current streak", description = "Calculates the current consecutive-completion streak for one of the authenticated user's habits.")
	public ApiResponse<HabitStreakResponse> currentStreak(
			@PathVariable String habitId
	) {
		return ApiResponse.success(statsService.calculateCurrentStreak(currentUser.id(), habitId, MoodlyTime.today(clock)));
	}
}
