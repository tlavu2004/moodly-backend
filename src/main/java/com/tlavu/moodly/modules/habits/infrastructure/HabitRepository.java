package com.tlavu.moodly.modules.habits.infrastructure;

import com.tlavu.moodly.modules.habits.domain.Habit;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HabitRepository extends MongoRepository<Habit, String> {

	List<Habit> findByUserIdAndActiveTrue(String userId);
}
