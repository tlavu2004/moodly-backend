package com.tlavu.moodly.modules.habits.service.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.habits.domain.TargetFrequency;
import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.shared.error.exception.common.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnedHabitServiceTest {
	@Mock
	private HabitRepository habitRepository;
	@InjectMocks
	private OwnedHabitService ownedHabitService;

	@Test
	void returnsOnlyAnOwnedHabit() {
		Habit habit = new Habit("habit-1", "user-1", "Read", "book", TargetFrequency.DAILY, true);
		when(habitRepository.findByIdAndUserId("habit-1", "user-1")).thenReturn(Optional.of(habit));

		assertThat(ownedHabitService.require("user-1", "habit-1")).isSameAs(habit);
	}

	@Test
	void hidesMissingAndOtherUsersHabitsBehindTheSameError() {
		when(habitRepository.findByIdAndUserId("habit-1", "user-1")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> ownedHabitService.require("user-1", "habit-1"))
				.isInstanceOf(ResourceNotFoundException.class)
				.hasMessage("Habit was not found.")
				.satisfies(error -> assertThat(((ResourceNotFoundException) error).getCode().getCode())
						.isEqualTo("NOT_FOUND"));
	}
}
