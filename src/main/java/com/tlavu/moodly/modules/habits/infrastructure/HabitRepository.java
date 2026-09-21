package com.tlavu.moodly.modules.habits.infrastructure;

import com.tlavu.moodly.modules.habits.domain.Habit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HabitRepository extends MongoRepository<Habit, String> {

	List<Habit> findByUserIdAndActiveTrue(String userId);

	List<Habit> findByUserId(String userId);

	List<Habit> findByUserIdAndActiveFalse(String userId);

	Optional<Habit> findByIdAndUserId(String id, String userId);
}
