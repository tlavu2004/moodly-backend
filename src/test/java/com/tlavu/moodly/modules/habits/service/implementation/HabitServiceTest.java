package com.tlavu.moodly.modules.habits.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.domain.TargetFrequency;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.modules.habits.service.input.CreateHabitInput;
import com.tlavu.moodly.modules.habits.service.input.UpdateHabitInput;
import com.tlavu.moodly.modules.habits.service.view.HabitView;
import com.tlavu.moodly.modules.habits.exception.HabitException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

	@Mock
	private HabitRepository habitRepository;
	@Mock
	private OwnedHabitService ownedHabitService;

	@InjectMocks
	private HabitService habitService;

	@Test
	void createsAnActiveHabitForTheUser() {
		when(habitRepository.save(org.mockito.ArgumentMatchers.any(Habit.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		HabitView result = habitService.create("user-1", new CreateHabitInput("Exercise", "🏃", TargetFrequency.DAILY));

		ArgumentCaptor<Habit> captor = ArgumentCaptor.forClass(Habit.class);
		verify(habitRepository).save(captor.capture());
		assertNotNull(result.id());
		assertEquals("user-1", result.userId());
		assertEquals("Exercise", result.name());
		assertEquals(TargetFrequency.DAILY, result.targetFrequency());
		assertTrue(result.active());
	}

	@Test
	void findsOnlyActiveHabitsForTheUser() {
		List<Habit> habits = List.of(new Habit("habit-1", "user-1", "Read", "📚", TargetFrequency.DAILY, true));
		when(habitRepository.findByUserIdAndActiveTrue("user-1")).thenReturn(habits);

		List<HabitView> result = habitService.findActive("user-1");
		habits.getFirst().update("Changed in persistence", null);

		assertEquals("Read", result.getFirst().name());
		assertEquals("📚", result.getFirst().icon());
		verify(habitRepository).findByUserIdAndActiveTrue("user-1");
	}

	@Test
	void updatesAndArchivesOnlyAnOwnedHabitAtTheExpectedVersion() {
		Habit habit = new Habit("habit-1", "user-1", "Read", "📚", TargetFrequency.DAILY, true);
		when(ownedHabitService.require("user-1", "habit-1")).thenReturn(habit);
		when(habitRepository.save(habit)).thenReturn(habit);

		HabitView updated = habitService.update(
				"user-1",
				"habit-1",
				new UpdateHabitInput("Read more", "📖", 0L)
		);
		assertEquals("Read more", updated.name());

		HabitView archived = habitService.archive("user-1", "habit-1", 0L);
		assertFalse(archived.active());
	}

	@Test
	void rejectsAStaleHabitVersionWithTheHabitConflictCode() {
		Habit habit = new Habit("habit-1", "user-1", "Read", "📚", TargetFrequency.DAILY, true);
		when(ownedHabitService.require("user-1", "habit-1")).thenReturn(habit);

		HabitException exception = assertThrows(HabitException.class, () -> habitService.archive("user-1", "habit-1", 1L));

		assertEquals("HABIT_CONFLICT", exception.getCode().getCode());
		verify(habitRepository, org.mockito.Mockito.never()).save(habit);
	}

	@Test
	void translatesAConcurrentRepositoryWriteToTheHabitConflictCode() {
		Habit habit = new Habit("habit-1", "user-1", "Read", null, TargetFrequency.DAILY, true);
		when(ownedHabitService.require("user-1", "habit-1")).thenReturn(habit);
		OptimisticLockingFailureException race = new OptimisticLockingFailureException("stale Mongo version");
		when(habitRepository.save(habit)).thenThrow(race);

		HabitException exception = assertThrows(HabitException.class,
				() -> habitService.archive("user-1", "habit-1", 0L));

		assertEquals("HABIT_CONFLICT", exception.getCode().getCode());
		assertEquals("Resource was changed by another request. Refresh and retry.", exception.getMessage());
		assertEquals(race, exception.getCause());
	}
}
