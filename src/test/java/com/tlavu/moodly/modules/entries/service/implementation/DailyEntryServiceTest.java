package com.tlavu.moodly.modules.entries.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
class DailyEntryServiceTest {
	@Mock
	private DailyEntryRepository dailyEntryRepository;
	@Mock
	private MongoTemplate mongoTemplate;
	@InjectMocks
	private DailyEntryService dailyEntryService;

	@Test
	void delegatesRangeLookupToRepository() {
		LocalDate date = LocalDate.of(2026, 8, 5);
		List<DailyEntry> entries = List.of(new DailyEntry("user-1", date));
		when(dailyEntryRepository.findByUserIdAndDateRange("user-1", date.minusDays(1), date))
				.thenReturn(entries);

		assertEquals(entries.stream().map(DailyEntryView::from).toList(),
				dailyEntryService.findBetween("user-1", date.minusDays(1), date));
	}
}
