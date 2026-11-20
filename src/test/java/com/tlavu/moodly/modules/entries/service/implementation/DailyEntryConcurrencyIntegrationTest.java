package com.tlavu.moodly.modules.entries.service.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mongodb.client.result.UpdateResult;
import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.model.HabitLog;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.habits.domain.TargetFrequency;
import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.modules.habits.service.implementation.HabitService;
import com.tlavu.moodly.modules.habits.code.HabitErrorCode;
import com.tlavu.moodly.modules.habits.exception.HabitException;
import com.tlavu.moodly.modules.entries.service.input.SetMoodInput;
import com.tlavu.moodly.modules.entries.service.input.UpdateHabitLogInput;
import com.tlavu.moodly.support.MongoTestConfiguration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
@Import(MongoTestConfiguration.class)
class DailyEntryConcurrencyIntegrationTest {
	private static final int FIRST_WRITE_RACE_ROUNDS = 10;
	private static final int ARCHIVE_RACE_ROUNDS = 5;
	private static final String USER = "__test_concurrent_entry__";
	@Autowired
	private DailyEntryService service;
	@Autowired
	private DailyEntryRepository repository;
	@Autowired
	private HabitRepository habitRepository;
	@Autowired
	private HabitService habitService;
	@MockitoSpyBean
	private MongoTemplate mongoTemplate;

	@BeforeEach
	void seedHabits() {
		habitRepository.save(new Habit("walk", USER, "Walk", "walk", TargetFrequency.DAILY, true));
		habitRepository.save(new Habit("read", USER, "Read", "book", TargetFrequency.DAILY, true));
	}

	@AfterEach
	void cleanUp() {
		repository.deleteByUserId(USER);
		habitRepository.deleteAll(habitRepository.findByUserId(USER));
	}

	@Test
	void concurrentFirstMoodAndHabitWritesPreserveBothChanges() throws Exception {
		for (int round = 0; round < FIRST_WRITE_RACE_ROUNDS; round++) {
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

	@Test
	void concurrentWritesToTheSameMissingHabitLogAppendOnceThenReplace() throws Exception {
		LocalDate date = LocalDate.of(2026, 9, 2);
		service.setMood(USER, date, new SetMoodInput(3, List.of(), "Start"));
		service.updateHabitLog(USER, date, new UpdateHabitLogInput("read", true, "Read"));
		CyclicBarrier bothReplacementsMissed = new CyclicBarrier(2);
		doAnswer(invocation -> {
			UpdateResult result = (UpdateResult) invocation.callRealMethod();
			if (result.getMatchedCount() == 0) {
				bothReplacementsMissed.await(10, TimeUnit.SECONDS);
			}
			return result;
		}).when(mongoTemplate).updateFirst(
				argThat((Query query) -> "walk".equals(query.getQueryObject().get("habits.habitId"))),
				any(Update.class), eq(DailyEntry.class));

		runTogether(
				() -> service.updateHabitLog(USER, date, new UpdateHabitLogInput("walk", true, "Done")),
				() -> service.updateHabitLog(USER, date, new UpdateHabitLogInput("walk", false, "Skipped")));

		verify(mongoTemplate, times(3)).updateFirst(
				argThat((Query query) -> "walk".equals(query.getQueryObject().get("habits.habitId"))),
				any(Update.class), eq(DailyEntry.class));
		DailyEntry entry = repository.findByUserIdAndDate(USER, date).orElseThrow();
		assertThat(entry.getMood().getScore()).isEqualTo(3);
		assertThat(entry.getMood().getNote()).isEqualTo("Start");
		assertThat(entry.getHabits()).hasSize(2)
				.filteredOn(log -> log.getHabitId().equals("read"))
				.extracting(HabitLog::isDone, HabitLog::getNote)
				.containsExactly(tuple(true, "Read"));
		assertThat(entry.getHabits()).filteredOn(log -> log.getHabitId().equals("walk"))
				.extracting(HabitLog::isDone, HabitLog::getNote)
				.singleElement().isIn(tuple(true, "Done"), tuple(false, "Skipped"));
	}

	@Test
	void archiveAndNewLogRaceHasOnlyAcceptedOrArchivedOutcomes() throws Exception {
		for (int round = 0; round < ARCHIVE_RACE_ROUNDS; round++) {
			String habitId = "race-" + round;
			LocalDate date = LocalDate.of(2026, 10, 1).plusDays(round);
			habitRepository.save(new Habit(habitId, USER, "Race", "run", TargetFrequency.DAILY, true));
			AtomicBoolean rejected = new AtomicBoolean();
			runTogether(
					() -> habitService.archive(USER, habitId, 0L),
					() -> {
						try {
							service.updateHabitLog(USER, date, new UpdateHabitLogInput(habitId, true, null));
						} catch (HabitException expected) {
							if (expected.getCode() != HabitErrorCode.HABIT_ARCHIVED) {
								throw expected;
							}
							rejected.set(true);
						}
					});
			assertThat(habitRepository.findById(habitId).orElseThrow().isActive()).isFalse();
			assertThat(repository.findByUserIdAndDate(USER, date)
					.map(entry -> entry.getHabits().stream().anyMatch(log -> habitId.equals(log.getHabitId())))
					.orElse(false)).isNotEqualTo(rejected.get());
		}
	}

	private void runTogether(Runnable first, Runnable second) throws Exception {
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
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
