package com.tlavu.moodly.modules.entries.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.model.HabitLog;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.entries.service.implementation.DailyEntryService;
import com.tlavu.moodly.modules.entries.service.input.SetMoodInput;
import com.tlavu.moodly.modules.entries.service.input.UpdateHabitLogInput;
import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyEntryServiceTest {

	private static final LocalDate DATE = LocalDate.of(2026, 8, 5);

	@Mock
	private DailyEntryRepository dailyEntryRepository;

	@InjectMocks
	private DailyEntryService dailyEntryService;

	@Test
	void createsEntryAndAddsHabitLogWhenNoEntryExists() {
		when(dailyEntryRepository.findByUserIdAndDate("user-1", DATE)).thenReturn(Optional.empty());
		when(dailyEntryRepository.save(org.mockito.ArgumentMatchers.any(DailyEntry.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		DailyEntryView result = dailyEntryService.updateHabitLog("user-1", DATE, new UpdateHabitLogInput("exercise", true, "Done"));

		assertEquals(1, result.habits().size());
		assertEquals("exercise", result.habits().getFirst().habitId());
		assertTrue(result.habits().getFirst().done());
		assertNotNull(result.createdAt());
		assertNotNull(result.updatedAt());
		ArgumentCaptor<DailyEntry> savedEntry = ArgumentCaptor.forClass(DailyEntry.class);
		verify(dailyEntryRepository).save(savedEntry.capture());
		assertEquals(result.habits().getFirst().habitId(), savedEntry.getValue().getHabits().getFirst().getHabitId());
	}

	@Test
	void updatesExistingHabitLogWithoutReplacingCreatedAt() {
		DailyEntry entry = new DailyEntry("user-1", DATE);
		Instant createdAt = Instant.parse("2026-08-05T00:00:00Z");
		entry.setCreatedAt(createdAt);
		entry.getHabits().add(new HabitLog("exercise", false, "Old"));
		when(dailyEntryRepository.findByUserIdAndDate("user-1", DATE)).thenReturn(Optional.of(entry));
		when(dailyEntryRepository.save(entry)).thenReturn(entry);

		dailyEntryService.updateHabitLog("user-1", DATE, new UpdateHabitLogInput("exercise", true, "New"));

		assertEquals(1, entry.getHabits().size());
		assertTrue(entry.getHabits().getFirst().isDone());
		assertEquals("New", entry.getHabits().getFirst().getNote());
		assertEquals(createdAt, entry.getCreatedAt());
		assertNotNull(entry.getUpdatedAt());
	}

	@Test
	void setsMoodOnExistingEntry() {
		DailyEntry entry = new DailyEntry("user-1", DATE);
		when(dailyEntryRepository.findByUserIdAndDate("user-1", DATE)).thenReturn(Optional.of(entry));
		when(dailyEntryRepository.save(entry)).thenReturn(entry);

		dailyEntryService.setMood("user-1", DATE, new SetMoodInput(4, List.of("calm"), "Good"));

		assertEquals(4, entry.getMood().getScore());
		assertEquals(List.of("calm"), entry.getMood().getTags());
		assertEquals("Good", entry.getMood().getNote());
	}

	@Test
	void delegatesRangeLookupToRepository() {
		List<DailyEntry> entries = List.of(new DailyEntry("user-1", DATE));
		when(dailyEntryRepository.findByUserIdAndDateRange("user-1", DATE.minusDays(1), DATE))
				.thenReturn(entries);

		assertEquals(entries.stream().map(DailyEntryView::from).toList(), dailyEntryService.findBetween("user-1", DATE.minusDays(1), DATE));
	}
}
