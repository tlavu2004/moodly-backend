package com.tlavu.moodly.modules.habits.service;

import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.modules.habits.service.command.CreateHabitCommand;
import com.tlavu.moodly.modules.habits.service.command.UpdateHabitCommand;
import java.util.List;
import java.util.UUID;
import com.tlavu.moodly.shared.error.exception.common.ResourceNotFoundException;
import com.tlavu.moodly.shared.error.exception.common.ConflictException;
import org.springframework.stereotype.Service;

@Service
public class HabitService {

	private final HabitRepository habitRepository;

	public HabitService(HabitRepository habitRepository) {
		this.habitRepository = habitRepository;
	}

	public Habit create(String userId, CreateHabitCommand command) {
		var habit = new Habit(
				UUID.randomUUID().toString(),
				userId,
				command.name(),
				command.icon(),
				command.targetFrequency(),
				true
		);
		return habitRepository.save(habit);
	}

	public List<Habit> findActive(String userId) {
		return habitRepository.findByUserIdAndActiveTrue(userId);
	}

	public List<Habit> findByStatus(String userId, HabitStatusFilter filter) {
		return switch (filter) {
			case ACTIVE -> habitRepository.findByUserIdAndActiveTrue(userId);
			case ARCHIVED -> habitRepository.findByUserIdAndActiveFalse(userId);
			case ALL -> habitRepository.findByUserId(userId);
		};
	}

	public Habit update(String userId, String habitId, UpdateHabitCommand command) {
		var habit = ownedHabit(userId, habitId);
		verifyVersion(habit, command.version());
		habit.update(command.name(), command.icon());
		return habitRepository.save(habit);
	}

	public Habit archive(String userId, String habitId, long version) {
		var habit = ownedHabit(userId, habitId);
		verifyVersion(habit, version);
		habit.archive();
		return habitRepository.save(habit);
	}

	public Habit restore(String userId, String habitId, long version) {
		var habit = ownedHabit(userId, habitId);
		verifyVersion(habit, version);
		habit.restore();
		return habitRepository.save(habit);
	}

	private Habit ownedHabit(String userId, String habitId) {
		return habitRepository.findByIdAndUserId(habitId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Habit was not found."));
	}

	private void verifyVersion(Habit habit, long requestedVersion) {
		long currentVersion = habit.getVersion() == null ? 0 : habit.getVersion();
		if (currentVersion != requestedVersion) {
			throw new ConflictException();
		}
	}
}
