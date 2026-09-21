package com.tlavu.moodly.modules.entries.presentation;

import com.tlavu.moodly.modules.entries.application.DailyEntryService;
import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.auth.application.CurrentUser;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import com.tlavu.moodly.shared.presentation.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.Clock;
import com.tlavu.moodly.shared.time.MoodlyTime;
import java.util.List;
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
public class DailyEntryController {

	private final DailyEntryService dailyEntryService;
	private final CurrentUser currentUser;
	private final Clock clock;

	public DailyEntryController(DailyEntryService dailyEntryService, CurrentUser currentUser, Clock clock) {
		this.dailyEntryService = dailyEntryService;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	@PatchMapping("/today")
	@Operation(summary = "Update today's habit log", description = "Marks or unmarks a habit in the authenticated user's entry for today.")
	public ApiResponse<DailyEntry> updateTodayHabit(
			@Valid @RequestBody UpdateHabitLogRequest request
	) {
		return ApiResponse.success(dailyEntryService.updateHabitLog(currentUser.id(), MoodlyTime.today(clock), request));
	}

	@PutMapping("/today/mood")
	@Operation(summary = "Set today's mood", description = "Sets the mood in the authenticated user's entry for today.")
	public ApiResponse<DailyEntry> setTodayMood(
			@Valid @RequestBody SetMoodRequest request
	) {
		return ApiResponse.success(dailyEntryService.setMood(currentUser.id(), MoodlyTime.today(clock), request));
	}

	@GetMapping
	@Operation(summary = "List entries by date range", description = "Returns the authenticated user's daily entries from `from` through `to`, inclusive. Future dates are not allowed.")
	public ApiResponse<PageResponse<DailyEntry>> findBetween(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		validatePage(page, size);
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("The 'from' date must not be after the 'to' date.");
		}
		if (from.isAfter(MoodlyTime.today(clock)) || to.isAfter(MoodlyTime.today(clock))) {
			throw new IllegalArgumentException("Entry dates must not be in the future.");
		}
		return ApiResponse.success(PageResponse.from(dailyEntryService.findBetween(currentUser.id(), from, to, page, size)));
	}

	@GetMapping("/today")
	@Operation(summary = "Get today's entry", description = "Returns a structured empty state when the authenticated user has not checked in today.")
	public ApiResponse<TodayEntryResponse> today() {
		var today = MoodlyTime.today(clock);
		return ApiResponse.success(dailyEntryService.findByDate(currentUser.id(), today)
				.map(entry -> new TodayEntryResponse(today, true, entry))
				.orElseGet(() -> TodayEntryResponse.empty(today)));
	}

	private void validatePage(int page, int size) {
		if (page < 0) throw new IllegalArgumentException("The 'page' parameter must be at least 0.");
		if (size < 1 || size > 100) throw new IllegalArgumentException("The 'size' parameter must be between 1 and 100.");
	}
}
