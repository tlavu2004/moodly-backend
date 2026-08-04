package com.tlavu.moodly.modules.entries.infrastructure;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

@SpringBootTest
class DailyEntryRepositoryIntegrationTest {

	private static final String TEST_USER_ID = "__test_duplicate_user__";

	@Autowired
	private DailyEntryRepository dailyEntryRepository;

	@AfterEach
	void cleanUp() {
		dailyEntryRepository.deleteByUserId(TEST_USER_ID);
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
}
