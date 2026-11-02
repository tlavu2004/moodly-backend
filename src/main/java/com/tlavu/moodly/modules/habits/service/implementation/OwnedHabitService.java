package com.tlavu.moodly.modules.habits.service.implementation;

import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.modules.habits.exception.HabitException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OwnedHabitService {
	private final HabitRepository habitRepository;

	public Habit require(String userId, String habitId) {
		return habitRepository.findByIdAndUserId(habitId, userId)
				.orElseThrow(HabitException::notFound);
	}
}
