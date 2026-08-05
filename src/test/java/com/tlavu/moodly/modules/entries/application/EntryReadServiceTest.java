package com.tlavu.moodly.modules.entries.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.entries.infrastructure.DailyEntryRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntryReadServiceTest {

	@Mock
	private DailyEntryRepository dailyEntryRepository;

	@InjectMocks
	private EntryReadService entryReadService;

	@Test
	void delegatesDescendingLookupToRepository() {
		var date = LocalDate.of(2026, 8, 5);
		var entries = List.of(new DailyEntry("user-1", date));
		when(dailyEntryRepository.findByUserIdAndDateLessThanEqualOrderByDateDesc("user-1", date)).thenReturn(entries);

		assertEquals(entries, entryReadService.findOnOrBefore("user-1", date));
		verify(dailyEntryRepository).findByUserIdAndDateLessThanEqualOrderByDateDesc("user-1", date);
	}
}
