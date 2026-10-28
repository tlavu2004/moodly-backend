package com.tlavu.moodly.modules.habits.controller;

import com.tlavu.moodly.modules.auth.security.context.CurrentUser;
import com.tlavu.moodly.modules.habits.domain.TargetFrequency;
import com.tlavu.moodly.modules.habits.dto.request.CreateHabitRequest;
import com.tlavu.moodly.modules.habits.dto.request.HabitVersionRequest;
import com.tlavu.moodly.modules.habits.dto.request.UpdateHabitRequest;
import com.tlavu.moodly.modules.habits.dto.response.HabitResponse;
import com.tlavu.moodly.modules.habits.service.filter.HabitStatusFilter;
import com.tlavu.moodly.modules.habits.service.implementation.HabitService;
import com.tlavu.moodly.modules.habits.service.input.CreateHabitInput;
import com.tlavu.moodly.modules.habits.service.input.UpdateHabitInput;
import com.tlavu.moodly.shared.controller.dto.response.ApiResponse;
import com.tlavu.moodly.shared.error.exception.common.InvalidRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/habits")
@Tag(name = "Habits", description = "Habit management for the authenticated user")
@RequiredArgsConstructor
public class HabitController {
	private final HabitService habitService;
	private final CurrentUser currentUser;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(
			summary = "Create a habit",
			description = "Creates a new habit owned by the authenticated user."
	)
	public ApiResponse<HabitResponse> create(
			@Valid @RequestBody CreateHabitRequest request
	) {
		CreateHabitInput input = new CreateHabitInput(
				request.name(),
				request.icon(),
				TargetFrequency.valueOf(request.targetFrequency())
		);
		HabitResponse response = HabitResponse.from(habitService.create(currentUser.id(), input));
		return ApiResponse.success(response);
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
		HabitStatusFilter filter = parseStatus(status);
		return ApiResponse.success(habitService.findByStatus(currentUser.id(), filter)
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
		UpdateHabitInput input = new UpdateHabitInput(
				request.name(),
				request.icon(),
				request.version()
		);
		HabitResponse response = HabitResponse.from(
				habitService.update(currentUser.id(), habitId, input)
		);

		return ApiResponse.success(response);
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

	private HabitStatusFilter parseStatus(String status) {
		return switch (status.toLowerCase(Locale.ROOT)) {
			case "active" -> HabitStatusFilter.ACTIVE;
			case "archived" -> HabitStatusFilter.ARCHIVED;
			case "all" -> HabitStatusFilter.ALL;
			default -> throw new InvalidRequestException("status must be one of: active, archived, all");
		};
	}
}
