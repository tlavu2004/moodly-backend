package com.tlavu.moodly.modules.entries.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.entries.service.input.UpdateHabitLogInput;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.modules.entries.service.view.DailyEntryView;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class DailyEntryServiceTest {
	@Mock
	private DailyEntryRepository dailyEntryRepository;
	@Mock
	private HabitRepository habitRepository;
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

	@Test
	void refusesUnknownHabitBeforeCreatingAnEntry() {
		LocalDate date = LocalDate.of(2026, 8, 5);
		when(habitRepository.findByIdAndUserId("other-habit", "user-1")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> dailyEntryService.updateHabitLog("user-1", date,
				new UpdateHabitLogInput("other-habit", true, null)))
				.isInstanceOf(com.tlavu.moodly.shared.error.exception.common.ResourceNotFoundException.class)
				.hasMessage("Habit was not found.");
		org.mockito.Mockito.verifyNoInteractions(mongoTemplate);
	}

	@Test
	void reportsUnrelatedEntryUpsertCollisionAsInternalFailure() {
		LocalDate date = LocalDate.of(2026, 8, 5);
		DuplicateKeyException collision = new DuplicateKeyException("different unique index");
		doThrow(collision).when(mongoTemplate).findAndModify(
				any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(DailyEntry.class));
		when(dailyEntryRepository.findByUserIdAndDate("user-1", date)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> dailyEntryService.setMood("user-1", date,
				new com.tlavu.moodly.modules.entries.service.input.SetMoodInput(3, List.of(), null)))
				.isInstanceOf(IllegalStateException.class)
				.hasCause(collision);
	}
}
