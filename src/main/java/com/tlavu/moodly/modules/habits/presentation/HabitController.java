package com.tlavu.moodly.modules.habits.presentation;

import com.tlavu.moodly.modules.habits.application.HabitService;
import com.tlavu.moodly.modules.habits.domain.Habit;
import com.tlavu.moodly.modules.auth.application.CurrentUser;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/habits")
@Tag(name = "Habits", description = "Habit management for the authenticated user")
public class HabitController {

	private final HabitService habitService;
	private final CurrentUser currentUser;

	public HabitController(HabitService habitService, CurrentUser currentUser) {
		this.habitService = habitService;
		this.currentUser = currentUser;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a habit", description = "Creates a new habit owned by the authenticated user.")
	public ApiResponse<Habit> create(
			@Valid @RequestBody CreateHabitRequest request
	) {
		return ApiResponse.success(habitService.create(currentUser.id(), request));
	}

	@GetMapping
	@Operation(summary = "List active habits", description = "Returns all active habits owned by the authenticated user.")
	public ApiResponse<List<Habit>> findActive() {
		return ApiResponse.success(habitService.findActive(currentUser.id()));
	}
}
