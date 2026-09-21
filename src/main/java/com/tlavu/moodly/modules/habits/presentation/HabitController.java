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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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
	@Operation(summary = "List habits", description = "Returns habits owned by the authenticated user, filtered by lifecycle status.")
	public ApiResponse<List<Habit>> findByStatus(
			@io.swagger.v3.oas.annotations.Parameter(description = "Lifecycle filter", schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"active", "archived", "all"}))
			@RequestParam(defaultValue = "active") String status
	) {
		return ApiResponse.success(habitService.findByStatus(currentUser.id(), status));
	}

	@PatchMapping("/{habitId}")
	@Operation(summary = "Update a habit", description = "Updates the name and icon. The version prevents lost concurrent updates.")
	public ApiResponse<Habit> update(@PathVariable String habitId, @Valid @RequestBody UpdateHabitRequest request) {
		return ApiResponse.success(habitService.update(currentUser.id(), habitId, request));
	}

	@PostMapping("/{habitId}/archive")
	@Operation(summary = "Archive a habit", description = "Deactivates the habit without changing historical entry logs.")
	public ApiResponse<Habit> archive(@PathVariable String habitId, @Valid @RequestBody HabitVersionRequest request) {
		return ApiResponse.success(habitService.archive(currentUser.id(), habitId, request.version()));
	}

	@PostMapping("/{habitId}/restore")
	@Operation(summary = "Restore a habit", description = "Makes an archived habit active again.")
	public ApiResponse<Habit> restore(@PathVariable String habitId, @Valid @RequestBody HabitVersionRequest request) {
		return ApiResponse.success(habitService.restore(currentUser.id(), habitId, request.version()));
	}
}
