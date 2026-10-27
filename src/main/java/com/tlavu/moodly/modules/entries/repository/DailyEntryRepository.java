package com.tlavu.moodly.modules.entries.repository;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DailyEntryRepository extends MongoRepository<DailyEntry, String> {
	Optional<DailyEntry> findByUserIdAndDate(String userId, LocalDate date);

	List<DailyEntry> findByUserId(String userId);

	@Query(
			value = "{ 'userId': ?0, 'date': { $gte: ?1, $lte: ?2 } }",
			sort = "{ 'date': 1 }"
	)
	List<DailyEntry> findByUserIdAndDateRange(
			String userId,
			LocalDate from,
			LocalDate to
	);

	@Query(value = "{ 'userId': ?0, 'date': { $gte: ?1, $lte: ?2 } }")
	Page<DailyEntry> findPageByUserIdAndDateRange(String userId, LocalDate from, LocalDate to, Pageable pageable);

	List<DailyEntry> findByUserIdAndDateLessThanEqualOrderByDateDesc(String userId, LocalDate date);

	void deleteByUserId(String userId);
}
