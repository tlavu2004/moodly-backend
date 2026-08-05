package com.tlavu.moodly.modules.entries.infrastructure;

import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DailyEntryRepository extends MongoRepository<DailyEntry, String> {

	Optional<DailyEntry> findByUserIdAndDate(String userId, LocalDate date);

	List<DailyEntry> findByUserIdAndDateBetweenOrderByDateAsc(String userId, LocalDate from, LocalDate to);

	List<DailyEntry> findByUserIdAndDateLessThanEqualOrderByDateDesc(String userId, LocalDate date);

	void deleteByUserId(String userId);
}
