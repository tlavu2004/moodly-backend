package com.tlavu.moodly.modules.entries.service;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.model.Mood;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.entries.service.input.SetMoodInput;
import com.tlavu.moodly.modules.entries.service.input.UpdateHabitLogInput;
import com.tlavu.moodly.modules.entries.service.view.EntryView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DailyEntryService {

	private final DailyEntryRepository dailyEntryRepository;

	public EntryView updateHabitLog(String userId, LocalDate date, UpdateHabitLogInput input) {
		var entry = getOrCreate(userId, date);
		var existing = entry.getHabits().stream()
				.filter(log -> log.getHabitId().equals(input.habitId()))
				.findFirst();
		if (existing.isPresent()) {
			var log = existing.get();
			log.setDone(input.done());
			log.setNote(input.note());
		} else {
			entry.getHabits().add(new DailyEntry.HabitLog(input.habitId(), input.done(), input.note()));
		}
		return EntryView.from(save(entry));
	}

	public EntryView setMood(String userId, LocalDate date, SetMoodInput input) {
		var entry = getOrCreate(userId, date);
		entry.setMood(new Mood(input.score(), input.tags(), input.note()));
		return EntryView.from(save(entry));
	}

	public List<EntryView> findBetween(String userId, LocalDate from, LocalDate to) {
		return dailyEntryRepository.findByUserIdAndDateRange(userId, from, to).stream()
				.map(EntryView::from)
				.toList();
	}

	public Page<EntryView> findBetween(String userId, LocalDate from, LocalDate to, int page, int size) {
		return dailyEntryRepository.findPageByUserIdAndDateRange(
				userId,
				from,
				to,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date"))
		).map(EntryView::from);
	}

	public Optional<EntryView> findByDate(String userId, LocalDate date) {
		return dailyEntryRepository.findByUserIdAndDate(userId, date).map(EntryView::from);
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
