package com.tlavu.moodly.modules.entries.service;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class EntryReadService {

	private final DailyEntryRepository dailyEntryRepository;

	public EntryReadService(DailyEntryRepository dailyEntryRepository) {
		this.dailyEntryRepository = dailyEntryRepository;
	}

	public List<DailyEntry> findOnOrBefore(String userId, LocalDate date) {
		return dailyEntryRepository.findByUserIdAndDateLessThanEqualOrderByDateDesc(userId, date);
	}

	public Optional<DailyEntry> findByDate(String userId, LocalDate date) {
		return dailyEntryRepository.findByUserIdAndDate(userId, date);
	}
}
