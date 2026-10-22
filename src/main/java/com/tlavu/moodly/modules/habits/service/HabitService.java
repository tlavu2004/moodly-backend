package com.tlavu.moodly.modules.habits.service;

import com.tlavu.moodly.modules.habits.dto.CreateHabitRequest;
import com.tlavu.moodly.modules.habits.dto.UpdateHabitRequest;
import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.model.TargetFrequency;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import java.util.List;
import java.util.UUID;
import com.tlavu.moodly.shared.error.exception.common.ResourceNotFoundException;
import com.tlavu.moodly.shared.error.exception.common.InvalidRequestException;
import com.tlavu.moodly.shared.error.exception.common.ConflictException;
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
				TargetFrequency.valueOf(request.targetFrequency()),
				true
		);
		return habitRepository.save(habit);
	}

	public List<Habit> findActive(String userId) {
		return habitRepository.findByUserIdAndActiveTrue(userId);
	}

	public List<Habit> findByStatus(String userId, String status) {
		return switch (status.toLowerCase()) {
			case "active" -> habitRepository.findByUserIdAndActiveTrue(userId);
			case "archived" -> habitRepository.findByUserIdAndActiveFalse(userId);
			case "all" -> habitRepository.findByUserId(userId);
			default -> throw new InvalidRequestException("status must be one of: active, archived, all");
		};
	}

	public Habit update(String userId, String habitId, UpdateHabitRequest request) {
		var habit = ownedHabit(userId, habitId);
		verifyVersion(habit, request.version());
		habit.update(request.name(), request.icon());
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
