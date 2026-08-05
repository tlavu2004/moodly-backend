package com.tlavu.moodly.modules.habits.application;

import com.tlavu.moodly.modules.habits.presentation.CreateHabitRequest;
import com.tlavu.moodly.modules.habits.domain.Habit;
import com.tlavu.moodly.modules.habits.infrastructure.HabitRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class HabitService {

	private final HabitRepository habitRepository;

	public HabitService(HabitRepository habitRepository) {
		this.habitRepository = habitRepository;
	}

	public Habit create(String userId, CreateHabitRequest request) {
		var habit = new Habit(
				UUID.randomUUID().toString(),
				userId,
				request.name(),
				request.icon(),
				request.targetFrequency(),
				true
		);
		return habitRepository.save(habit);
	}

	public List<Habit> findActive(String userId) {
		return habitRepository.findByUserIdAndActiveTrue(userId);
	}
}
