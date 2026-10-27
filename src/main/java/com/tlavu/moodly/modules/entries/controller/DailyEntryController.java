package com.tlavu.moodly.modules.entries.controller;

import com.tlavu.moodly.modules.auth.security.context.CurrentUser;
import com.tlavu.moodly.modules.entries.dto.request.SetMoodRequest;
import com.tlavu.moodly.modules.entries.dto.request.UpdateHabitLogRequest;
import com.tlavu.moodly.modules.entries.dto.response.DailyEntryResponse;
import com.tlavu.moodly.modules.entries.dto.response.TodayEntryResponse;
import com.tlavu.moodly.modules.entries.service.implementation.DailyEntryService;
import com.tlavu.moodly.modules.entries.service.input.SetMoodInput;
import com.tlavu.moodly.modules.entries.service.input.UpdateHabitLogInput;
import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
import com.tlavu.moodly.shared.controller.dto.response.ApiResponse;
import com.tlavu.moodly.shared.controller.dto.response.PageResponse;
import com.tlavu.moodly.shared.error.exception.common.InvalidRequestException;
import com.tlavu.moodly.shared.time.MoodlyTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/entries")
@Tag(name = "Daily entries", description = "Daily mood and habit-log entries for the authenticated user")
@RequiredArgsConstructor
public class DailyEntryController {
	private final DailyEntryService dailyEntryService;
	private final CurrentUser currentUser;
	private final Clock clock;

	@PatchMapping("/today")
	@Operation(
			summary = "Update today's habit log",
			description = "Marks or unmarks a habit in the authenticated user's entry for today."
	)
	public ApiResponse<DailyEntryResponse> updateTodayHabit(
			@Valid @RequestBody UpdateHabitLogRequest request
	) {
		UpdateHabitLogInput input = new UpdateHabitLogInput(
				request.habitId(),
				request.done(),
				request.note()
		);
		DailyEntryView view = dailyEntryService.updateHabitLog(
				currentUser.id(),
				MoodlyTime.today(clock),
				input
		);
		DailyEntryResponse response = DailyEntryResponse.from(view);

		return ApiResponse.success(response);
	}

	@PutMapping("/today/mood")
	@Operation(
			summary = "Set today's mood",
			description = "Sets the mood in the authenticated user's entry for today."
	)
	public ApiResponse<DailyEntryResponse> setTodayMood(
			@Valid @RequestBody SetMoodRequest request
	) {
		SetMoodInput input = new SetMoodInput(
				request.score(),
				request.tags(),
				request.note()
		);
		DailyEntryView view = dailyEntryService.setMood(
				currentUser.id(),
				MoodlyTime.today(clock),
				input
		);
		DailyEntryResponse response = DailyEntryResponse.from(view);

		return ApiResponse.success(response);
	}

	@GetMapping
	@Operation(summary = "List entries by date range", description = "Returns the authenticated user's daily entries from `from` through `to`, inclusive. Future dates are not allowed.")
	public ApiResponse<PageResponse<DailyEntryResponse>> findBetween(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		if (page < 0) {
			throw new InvalidRequestException("The 'page' parameter must be at least 0.");
		}
		if (size < 1 || size > 100) {
			throw new InvalidRequestException("The 'size' parameter must be between 1 and 100.");
		}
		if (from.isAfter(to)) {
			throw new InvalidRequestException("The 'from' date must not be after the 'to' date.");
		}
		if (
				from.isAfter(MoodlyTime.today(clock)) ||
				to.isAfter(MoodlyTime.today(clock))
		) {
			throw new InvalidRequestException("Entry dates must not be in the future.");
		}

		Page<DailyEntryView> views = dailyEntryService.findBetween(
				currentUser.id(),
				from,
				to,
				page,
				size
		);
		PageResponse<DailyEntryResponse> response = PageResponse.from(views.map(DailyEntryResponse::from));

		return ApiResponse.success(response);
	}

	@GetMapping("/today")
	@Operation(
			summary = "Get today's entry",
			description = "Returns a structured empty state when the authenticated user has not checked in today."
	)
	public ApiResponse<TodayEntryResponse> today() {
		var today = MoodlyTime.today(clock);
		Optional<DailyEntryView> view = dailyEntryService.findByDate(currentUser.id(), today);

		TodayEntryResponse response = view.map(
				entry -> new TodayEntryResponse(
						today,
						true,
						DailyEntryResponse.from(entry)
				)
		).orElseGet(() -> TodayEntryResponse.empty(today));

		return ApiResponse.success(response);
	}
}
