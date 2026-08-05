package com.tlavu.moodly.modules.entries.application;

import com.tlavu.moodly.modules.entries.presentation.SetMoodRequest;
import com.tlavu.moodly.modules.entries.presentation.UpdateHabitLogRequest;
import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.entries.infrastructure.DailyEntryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DailyEntryService {

	private final DailyEntryRepository dailyEntryRepository;

	public DailyEntryService(DailyEntryRepository dailyEntryRepository) {
		this.dailyEntryRepository = dailyEntryRepository;
	}

	public DailyEntry updateHabitLog(String userId, LocalDate date, UpdateHabitLogRequest request) {
		var entry = getOrCreate(userId, date);
		var existing = entry.getHabits().stream()
				.filter(log -> log.getHabitId().equals(request.habitId()))
				.findFirst();
		if (existing.isPresent()) {
			var log = existing.get();
			log.setDone(request.done());
			log.setNote(request.note());
		} else {
			entry.getHabits().add(new DailyEntry.HabitLog(request.habitId(), request.done(), request.note()));
		}
		return save(entry);
	}

	public DailyEntry setMood(String userId, LocalDate date, SetMoodRequest request) {
		var entry = getOrCreate(userId, date);
		entry.setMood(new DailyEntry.Mood(request.score(), request.tags(), request.note()));
		return save(entry);
	}

	public List<DailyEntry> findBetween(String userId, LocalDate from, LocalDate to) {
		return dailyEntryRepository.findByUserIdAndDateRange(userId, from, to);
	}

	private DailyEntry getOrCreate(String userId, LocalDate date) {
		return dailyEntryRepository.findByUserIdAndDate(userId, date)
				.orElseGet(() -> new DailyEntry(userId, date));
	}

	private DailyEntry save(DailyEntry entry) {
		var now = Instant.now();
		if (entry.getCreatedAt() == null) {
			entry.setCreatedAt(now);
		}
		entry.setUpdatedAt(now);
		return dailyEntryRepository.save(entry);
	}
}
