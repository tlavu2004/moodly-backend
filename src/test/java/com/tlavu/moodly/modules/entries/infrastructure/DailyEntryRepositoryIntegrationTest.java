package com.tlavu.moodly.modules.entries.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.support.MongoTestConfiguration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(MongoTestConfiguration.class)
class DailyEntryRepositoryIntegrationTest {

	private static final String TEST_USER_ID = "__test_duplicate_user__";
	private static final String RANGE_USER_ID = "__test_range_user__";
	private static final String OTHER_USER_ID = "__test_other_range_user__";

	@Autowired
	private DailyEntryRepository dailyEntryRepository;

	@AfterEach
	void cleanUp() {
		dailyEntryRepository.deleteByUserId(TEST_USER_ID);
		dailyEntryRepository.deleteByUserId(RANGE_USER_ID);
		dailyEntryRepository.deleteByUserId(OTHER_USER_ID);
	}

	@Test
	void rejectsDuplicateEntryForTheSameUserAndDate() {
		var date = LocalDate.of(2026, 8, 4);
		dailyEntryRepository.save(new DailyEntry(TEST_USER_ID, date));

		assertThrows(
			DuplicateKeyException.class,
			() -> dailyEntryRepository.save(new DailyEntry(TEST_USER_ID, date))
		);
	}

	@Test
	void returnsBothDateBoundariesInAscendingOrder() {
		var from = LocalDate.of(2026, 8, 1);
		var middle = LocalDate.of(2026, 8, 2);
		var to = LocalDate.of(2026, 8, 3);
		dailyEntryRepository.saveAll(List.of(
				new DailyEntry(RANGE_USER_ID, to),
				new DailyEntry(RANGE_USER_ID, from),
				new DailyEntry(RANGE_USER_ID, middle),
				new DailyEntry(RANGE_USER_ID, from.minusDays(1)),
				new DailyEntry(RANGE_USER_ID, to.plusDays(1))
		));

		var entries = dailyEntryRepository.findByUserIdAndDateRange(RANGE_USER_ID, from, to);

		assertEquals(List.of(from, middle, to), entries.stream().map(DailyEntry::getDate).toList());
	}

	@Test
	void doesNotReturnEntriesFromAnotherUserInTheSameDateRange() {
		var date = LocalDate.of(2026, 8, 2);
		dailyEntryRepository.save(new DailyEntry(RANGE_USER_ID, date));
		dailyEntryRepository.save(new DailyEntry(OTHER_USER_ID, date));

		var entries = dailyEntryRepository.findByUserIdAndDateRange(RANGE_USER_ID, date, date);

		assertEquals(List.of(RANGE_USER_ID), entries.stream().map(DailyEntry::getUserId).toList());
	}
}
