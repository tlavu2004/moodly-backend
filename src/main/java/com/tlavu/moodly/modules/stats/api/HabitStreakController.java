package com.tlavu.moodly.modules.stats.api;

import com.tlavu.moodly.modules.stats.application.StatsService;
import com.tlavu.moodly.modules.auth.application.CurrentUser;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/habits")
public class HabitStreakController {

	private final StatsService statsService;
	private final CurrentUser currentUser;

	public HabitStreakController(StatsService statsService, CurrentUser currentUser) {
		this.statsService = statsService;
		this.currentUser = currentUser;
	}

	@GetMapping("/{habitId}/streak")
	public ApiResponse<HabitStreakResponse> currentStreak(
			@PathVariable String habitId
	) {
		return ApiResponse.success(statsService.calculateCurrentStreak(currentUser.id(), habitId, LocalDate.now()));
	}
}
