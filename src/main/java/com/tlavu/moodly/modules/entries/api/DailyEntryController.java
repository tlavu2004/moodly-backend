package com.tlavu.moodly.modules.entries.api;

import com.tlavu.moodly.modules.entries.application.DailyEntryService;
import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/entries")
public class DailyEntryController {

	private final DailyEntryService dailyEntryService;

	public DailyEntryController(DailyEntryService dailyEntryService) {
		this.dailyEntryService = dailyEntryService;
	}

	@PatchMapping("/today")
	public DailyEntry updateTodayHabit(
			@RequestHeader("X-User-Id") String userId,
			@Valid @RequestBody UpdateHabitLogRequest request
	) {
		return dailyEntryService.updateHabitLog(userId, LocalDate.now(), request);
	}

	@PutMapping("/today/mood")
	public DailyEntry setTodayMood(
			@RequestHeader("X-User-Id") String userId,
			@Valid @RequestBody SetMoodRequest request
	) {
		return dailyEntryService.setMood(userId, LocalDate.now(), request);
	}

	@GetMapping
	public List<DailyEntry> findBetween(
			@RequestHeader("X-User-Id") String userId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		return dailyEntryService.findBetween(userId, from, to);
	}
}
