package com.tlavu.moodly.modules.habits.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.habits.domain.Habit;
import com.tlavu.moodly.modules.habits.infrastructure.HabitRepository;
import com.tlavu.moodly.modules.habits.presentation.CreateHabitRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

	@Mock
	private HabitRepository habitRepository;

	@InjectMocks
	private HabitService habitService;

	@Test
	void createsAnActiveHabitForTheUser() {
		when(habitRepository.save(org.mockito.ArgumentMatchers.any(Habit.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		var result = habitService.create("user-1", new CreateHabitRequest("Exercise", "🏃", "daily"));

		var captor = ArgumentCaptor.forClass(Habit.class);
		verify(habitRepository).save(captor.capture());
		assertNotNull(result.getId());
		assertEquals("user-1", result.getUserId());
		assertEquals("Exercise", result.getName());
		assertEquals("daily", result.getTargetFrequency());
		assertTrue(result.isActive());
	}

	@Test
	void findsOnlyActiveHabitsForTheUser() {
		var habits = List.of(new Habit("habit-1", "user-1", "Read", "📚", "daily", true));
		when(habitRepository.findByUserIdAndActiveTrue("user-1")).thenReturn(habits);

		assertEquals(habits, habitService.findActive("user-1"));
		verify(habitRepository).findByUserIdAndActiveTrue("user-1");
	}
}
