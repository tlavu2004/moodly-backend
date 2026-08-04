package com.tlavu.moodly.modules.habits.api;

import com.tlavu.moodly.modules.habits.application.HabitService;
import com.tlavu.moodly.modules.habits.domain.Habit;
import com.tlavu.moodly.shared.api.dto.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/habits")
public class HabitController {

	private final HabitService habitService;

	public HabitController(HabitService habitService) {
		this.habitService = habitService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<Habit> create(
			@RequestHeader("X-User-Id") String userId,
			@Valid @RequestBody CreateHabitRequest request
	) {
		return ApiResponse.success(habitService.create(userId, request));
	}

	@GetMapping
	public ApiResponse<List<Habit>> findActive(@RequestHeader("X-User-Id") String userId) {
		return ApiResponse.success(habitService.findActive(userId));
	}
}
