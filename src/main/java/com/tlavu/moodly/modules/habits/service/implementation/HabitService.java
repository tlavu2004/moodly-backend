package com.tlavu.moodly.modules.habits.service.implementation;

import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.modules.habits.service.filter.HabitStatusFilter;
import com.tlavu.moodly.modules.habits.service.input.CreateHabitInput;
import com.tlavu.moodly.modules.habits.service.input.UpdateHabitInput;
import com.tlavu.moodly.modules.habits.service.view.HabitView;
import com.tlavu.moodly.shared.error.exception.common.ConflictException;
import com.tlavu.moodly.shared.error.exception.common.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class HabitService {

	private final HabitRepository habitRepository;

	public HabitService(HabitRepository habitRepository) {
		this.habitRepository = habitRepository;
	}

	public HabitView create(String userId, CreateHabitInput input) {
		Habit habit = new Habit(
				UUID.randomUUID().toString(),
				userId,
				input.name(),
				input.icon(),
				input.targetFrequency(),
				true
		);
		return HabitView.from(habitRepository.save(habit));
	}

	public List<HabitView> findActive(String userId) {
		return habitRepository.findByUserIdAndActiveTrue(userId).stream()
				.map(HabitView::from)
				.toList();
	}

	public List<HabitView> findByStatus(String userId, HabitStatusFilter filter) {
		List<Habit> habits = switch (filter) {
			case ACTIVE -> habitRepository.findByUserIdAndActiveTrue(userId);
			case ARCHIVED -> habitRepository.findByUserIdAndActiveFalse(userId);
			case ALL -> habitRepository.findByUserId(userId);
		};
		return habits.stream().map(HabitView::from).toList();
	}

	public HabitView update(String userId, String habitId, UpdateHabitInput input) {
		Habit habit = ownedHabit(userId, habitId);
		verifyVersion(habit, input.version());
		habit.update(input.name(), input.icon());

		return HabitView.from(habitRepository.save(habit));
	}

	public HabitView archive(String userId, String habitId, long version) {
		Habit habit = ownedHabit(userId, habitId);
		verifyVersion(habit, version);
		habit.archive();

		return HabitView.from(habitRepository.save(habit));
	}

	public HabitView restore(String userId, String habitId, long version) {
		Habit habit = ownedHabit(userId, habitId);
		verifyVersion(habit, version);
		habit.restore();

		return HabitView.from(habitRepository.save(habit));
	}

	private Habit ownedHabit(String userId, String habitId) {
		return habitRepository.findByIdAndUserId(habitId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Habit was not found."));
	}

	private void verifyVersion(Habit habit, long requestedVersion) {
		long currentVersion = habit.getVersion() == null
				? 0
				: habit.getVersion();

		if (currentVersion != requestedVersion) {
			throw new ConflictException();
		}
	}
}
