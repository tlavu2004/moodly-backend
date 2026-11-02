package com.tlavu.moodly.modules.entries.service.implementation;

import static org.assertj.core.api.Assertions.assertThat;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.entries.service.input.SetMoodInput;
import com.tlavu.moodly.modules.entries.service.input.UpdateHabitLogInput;
import com.tlavu.moodly.support.MongoTestConfiguration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(MongoTestConfiguration.class)
class DailyEntryConcurrencyIntegrationTest {
	private static final String USER = "__test_concurrent_entry__";
	@Autowired
	private DailyEntryService service;
	@Autowired
	private DailyEntryRepository repository;

	@AfterEach
	void cleanUp() {
		repository.deleteByUserId(USER);
	}

	@Test
	void concurrentFirstMoodAndHabitWritesPreserveBothChanges() throws Exception {
		for (int round = 0; round < 10; round++) {
			LocalDate date = LocalDate.of(2026, 8, 1).plusDays(round);
			runTogether(
					() -> service.setMood(USER, date, new SetMoodInput(4, List.of("calm"), "Good")),
					() -> service.updateHabitLog(USER, date, new UpdateHabitLogInput("walk", true, "Done")));
			DailyEntry entry = repository.findByUserIdAndDate(USER, date).orElseThrow();
			assertThat(entry.getMood().getScore()).isEqualTo(4);
			assertThat(entry.getHabits()).hasSize(1);
			assertThat(entry.getHabits().getFirst().getHabitId()).isEqualTo("walk");
			assertThat(repository.findByUserId(USER).stream().filter(item -> date.equals(item.getDate())))
					.hasSize(1);
		}
	}

	@Test
	void concurrentDistinctHabitWritesPreserveBothLogs() throws Exception {
		LocalDate date = LocalDate.of(2026, 9, 1);
		service.setMood(USER, date, new SetMoodInput(3, List.of(), "Start"));
		runTogether(
				() -> service.updateHabitLog(USER, date, new UpdateHabitLogInput("walk", true, "Done")),
				() -> service.updateHabitLog(USER, date, new UpdateHabitLogInput("read", true, "Done")));
		assertThat(repository.findByUserIdAndDate(USER, date).orElseThrow().getHabits())
				.extracting("habitId").containsExactlyInAnyOrder("walk", "read");
	}

	private void runTogether(Runnable first, Runnable second) throws Exception {
		try (var executor = Executors.newFixedThreadPool(2)) {
			CountDownLatch start = new CountDownLatch(1);
			Future<?> one = executor.submit(() -> { await(start); first.run(); });
			Future<?> two = executor.submit(() -> { await(start); second.run(); });
			start.countDown();
			one.get();
			two.get();
		}
	}

	private void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}
}
