package com.tlavu.moodly.modules.entries.service.implementation;

import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntryReadService {
	private final DailyEntryRepository dailyEntryRepository;

	public List<DailyEntryView> findOnOrBefore(String userId, LocalDate date) {
		return dailyEntryRepository.findByUserIdAndDateLessThanEqualOrderByDateDesc(userId, date)
				.stream()
				.map(DailyEntryView::from)
				.toList();
	}

	public Optional<DailyEntryView> findByDate(String userId, LocalDate date) {
		return dailyEntryRepository.findByUserIdAndDate(userId, date)
				.map(DailyEntryView::from);
	}
}
