package com.tlavu.moodly.modules.entries.presentation;

import com.tlavu.moodly.modules.entries.application.DailyEntryService;
import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.auth.application.CurrentUser;
import com.tlavu.moodly.shared.presentation.dto.response.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
public class DailyEntryController {

	private final DailyEntryService dailyEntryService;
	private final CurrentUser currentUser;

	public DailyEntryController(DailyEntryService dailyEntryService, CurrentUser currentUser) {
		this.dailyEntryService = dailyEntryService;
		this.currentUser = currentUser;
	}

	@PatchMapping("/today")
	public ApiResponse<DailyEntry> updateTodayHabit(
			@Valid @RequestBody UpdateHabitLogRequest request
	) {
		return ApiResponse.success(dailyEntryService.updateHabitLog(currentUser.id(), LocalDate.now(), request));
	}

	@PutMapping("/today/mood")
	public ApiResponse<DailyEntry> setTodayMood(
			@Valid @RequestBody SetMoodRequest request
	) {
		return ApiResponse.success(dailyEntryService.setMood(currentUser.id(), LocalDate.now(), request));
	}

	@GetMapping
	public ApiResponse<List<DailyEntry>> findBetween(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
				@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("The 'from' date must not be after the 'to' date.");
		}
		if (from.isAfter(LocalDate.now()) || to.isAfter(LocalDate.now())) {
			throw new IllegalArgumentException("Entry dates must not be in the future.");
		}
		return ApiResponse.success(dailyEntryService.findBetween(currentUser.id(), from, to));
	}
}
