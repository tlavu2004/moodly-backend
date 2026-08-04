package com.tlavu.moodly.modules.stats.api;

import com.tlavu.moodly.modules.stats.application.StatsService;
import com.tlavu.moodly.shared.api.dto.response.ApiResponse;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/habits")
public class HabitStreakController {

	private final StatsService statsService;

	public HabitStreakController(StatsService statsService) {
		this.statsService = statsService;
	}

	@GetMapping("/{habitId}/streak")
	public ApiResponse<HabitStreakResponse> currentStreak(
			@PathVariable String habitId,
			@RequestHeader("X-User-Id") String userId
	) {
		return ApiResponse.success(statsService.calculateCurrentStreak(userId, habitId, LocalDate.now()));
	}
}
