package com.tlavu.moodly.modules.habits.controller;

import com.tlavu.moodly.modules.auth.security.CurrentUser;
import com.tlavu.moodly.modules.habits.dto.CreateHabitRequest;
import com.tlavu.moodly.modules.habits.dto.HabitResponse;
import com.tlavu.moodly.modules.habits.dto.HabitVersionRequest;
import com.tlavu.moodly.modules.habits.dto.UpdateHabitRequest;
import com.tlavu.moodly.modules.habits.service.HabitService;
import com.tlavu.moodly.shared.controller.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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

	public HabitController(
			HabitService habitService,
			CurrentUser currentUser
	) {
		this.habitService = habitService;
		this.currentUser = currentUser;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(
			summary = "Create a habit",
			description = "Creates a new habit owned by the authenticated user."
	)
	public ApiResponse<HabitResponse> create(
			@Valid @RequestBody CreateHabitRequest request
	) {
		return ApiResponse.success(
				HabitResponse.from(
						habitService.create(
								currentUser.id(),
								request
						)
				)
		);
	}

	@GetMapping
	@Operation(
			summary = "List habits",
			description = "Returns habits owned by the authenticated user, filtered by lifecycle status."
	)
	public ApiResponse<List<HabitResponse>> findByStatus(
			@Parameter(
					description = "Lifecycle filter",
					schema = @Schema(allowableValues = {
						"active",
						"archived",
						"all"
					})
			)
			@RequestParam(defaultValue = "active") String status
	) {
		return ApiResponse.success(habitService.findByStatus(currentUser.id(), status)
				.stream()
				.map(HabitResponse::from)
				.toList());
	}

	@PatchMapping("/{habitId}")
	@Operation(
			summary = "Update a habit",
			description = "Updates the name and icon. The version prevents lost concurrent updates."
	)
	public ApiResponse<HabitResponse> update(
			@PathVariable String habitId,
			@Valid @RequestBody UpdateHabitRequest request
	) {
		return ApiResponse.success(
				HabitResponse.from(
						habitService.update(
								currentUser.id(),
								habitId,
								request
						)
				)
		);
	}

	@PostMapping("/{habitId}/archive")
	@Operation(
			summary = "Archive a habit",
			description = "Deactivates the habit without changing historical entry logs."
	)
	public ApiResponse<HabitResponse> archive(
			@PathVariable String habitId,
			@Valid @RequestBody HabitVersionRequest request
	) {
		return ApiResponse.success(
				HabitResponse.from(
						habitService.archive(
								currentUser.id(),
								habitId,
								request.version()
						)
				)
		);
	}

	@PostMapping("/{habitId}/restore")
	@Operation(
			summary = "Restore a habit",
			description = "Makes an archived habit active again."
	)
	public ApiResponse<HabitResponse> restore(
			@PathVariable String habitId,
			@Valid @RequestBody HabitVersionRequest request
	) {
		return ApiResponse.success(
				HabitResponse.from(
						habitService.restore(
								currentUser.id(),
								habitId,
								request.version()
						)
				)
		);
	}
}
