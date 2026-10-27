package com.tlavu.moodly.modules.entries.service.implementation;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.model.HabitLog;
import com.tlavu.moodly.modules.entries.model.Mood;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.entries.service.input.SetMoodInput;
import com.tlavu.moodly.modules.entries.service.input.UpdateHabitLogInput;
import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
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

	public DailyEntryView updateHabitLog(
			String userId,
			LocalDate date,
			UpdateHabitLogInput input
	) {
		DailyEntry entry = getOrCreate(userId, date);
		Optional<HabitLog> existing = entry.getHabits().stream()
				.filter(log -> log.getHabitId().equals(input.habitId()))
				.findFirst();

		if (existing.isPresent()) {
			HabitLog log = existing.get();
			log.setDone(input.done());
			log.setNote(input.note());
		} else {
			entry.getHabits().add(
					new HabitLog(
							input.habitId(),
							input.done(),
							input.note()
					)
			);
		}

		return DailyEntryView.from(save(entry));
	}

	public DailyEntryView setMood(
			String userId,
			LocalDate date,
			SetMoodInput input
	) {
		DailyEntry entry = getOrCreate(userId, date);
		entry.setMood(
				new Mood(
						input.score(),
						input.tags(),
						input.note()
				)
		);

		return DailyEntryView.from(save(entry));
	}

	public List<DailyEntryView> findBetween(
			String userId,
			LocalDate from,
			LocalDate to
	) {
		return dailyEntryRepository.findByUserIdAndDateRange(userId, from, to)
				.stream()
				.map(DailyEntryView::from)
				.toList();
	}

	public Page<DailyEntryView> findBetween(
			String userId,
			LocalDate from,
			LocalDate to,
			int page,
			int size
	) {
		return dailyEntryRepository.findPageByUserIdAndDateRange(
				userId,
				from,
				to,
				PageRequest.of(
						page,
						size,
						Sort.by(Sort.Direction.DESC, "date")
				)
		).map(DailyEntryView::from);
	}

	public Optional<DailyEntryView> findByDate(String userId, LocalDate date) {
		return dailyEntryRepository.findByUserIdAndDate(userId, date)
				.map(DailyEntryView::from);
	}

	private DailyEntry getOrCreate(String userId, LocalDate date) {
		return dailyEntryRepository.findByUserIdAndDate(userId, date)
				.orElseGet(() -> new DailyEntry(userId, date));
	}

	private DailyEntry save(DailyEntry entry) {
		Instant now = Instant.now();

		if (entry.getCreatedAt() == null) {
			entry.setCreatedAt(now);
		}

		entry.setUpdatedAt(now);

		return dailyEntryRepository.save(entry);
	}
}
