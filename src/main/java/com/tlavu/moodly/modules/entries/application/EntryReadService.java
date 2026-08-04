package com.tlavu.moodly.modules.entries.application;

import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.entries.infrastructure.DailyEntryRepository;
import java.time.LocalDate;
import java.util.List;
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
}
