package com.tlavu.moodly.modules.entries.service;

import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.entries.service.view.EntryView;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntryReadService {
	private final DailyEntryRepository dailyEntryRepository;

	public List<EntryView> findOnOrBefore(String userId, LocalDate date) {
		return dailyEntryRepository.findByUserIdAndDateLessThanEqualOrderByDateDesc(userId, date).stream()
				.map(EntryView::from)
				.toList();
	}

	public Optional<EntryView> findByDate(String userId, LocalDate date) {
		return dailyEntryRepository.findByUserIdAndDate(userId, date).map(EntryView::from);
	}
}
