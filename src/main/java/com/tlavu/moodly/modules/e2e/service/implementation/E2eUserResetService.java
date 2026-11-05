package com.tlavu.moodly.modules.e2e.service.implementation;

import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchClient;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchException;
import com.tlavu.moodly.modules.e2e.service.view.E2eResetView;
import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.model.HabitLog;
import com.tlavu.moodly.modules.entries.model.Mood;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.habits.domain.TargetFrequency;
import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchIndexManager;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchWriter;
import com.tlavu.moodly.modules.search.model.elasticsearch.DailyEntrySearchDocument;
import com.tlavu.moodly.shared.error.exception.common.SearchInfrastructureUnavailableException;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchTransportException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Resets only the fixed synthetic account reserved for browser E2E tests. */
@Service
@Profile("!prod & !production")
@ConditionalOnProperty(name = "moodly.e2e.reset.enabled", havingValue = "true")
public class E2eUserResetService {
	private static final String DEFAULT_USER_ID = "synthetic|moodly-playwright-e2e";
	private static final String WALK_HABIT_ID = "e2e-habit-walk";
	private static final String JOURNAL_HABIT_ID = "e2e-habit-journal";
	private static final List<String> ENTRY_IDS = List.of("e2e-entry-2026-09-08", "e2e-entry-2026-09-09", "e2e-entry-2026-09-10");

	private final HabitRepository habitRepository;
	private final DailyEntryRepository entryRepository;
	private final DailyEntrySearchWriter searchWriter;
	private final ElasticsearchClient elasticsearchClient;
	private final DailyEntrySearchIndexManager indexManager;
	private final String userId;

	public E2eUserResetService(
			HabitRepository habitRepository,
			DailyEntryRepository entryRepository,
			DailyEntrySearchWriter searchWriter,
			ElasticsearchClient elasticsearchClient,
			DailyEntrySearchIndexManager indexManager,
			@Value("${moodly.e2e.reset.user-id:" + DEFAULT_USER_ID + "}") String userId
	) {
		this.habitRepository = habitRepository;
		this.entryRepository = entryRepository;
		this.searchWriter = searchWriter;
		this.elasticsearchClient = elasticsearchClient;
		this.indexManager = indexManager;
		this.userId = userId;
	}

	/** Prevents reset calls from interleaving within this backend instance. */
	public synchronized E2eResetView reset() {
		try {
			// Remove only this synthetic subject's old projection, including any stale documents.
			elasticsearchClient.deleteByUser(indexManager.getIndexName(), userId);

			entryRepository.deleteByUserId(userId);
			habitRepository.deleteAll(habitRepository.findByUserId(userId));

			Habit walk = new Habit(
					WALK_HABIT_ID,
					userId,
					"Daily walk",
					"walk",
					TargetFrequency.DAILY,
					true
			);
			Habit journal = new Habit(
					JOURNAL_HABIT_ID,
					userId,
					"Evening journal",
					"book",
					TargetFrequency.DAILY,
					true
			);

			habitRepository.saveAll(List.of(walk, journal));

			List<DailyEntry> entries = List.of(
					entry(
							ENTRY_IDS.get(0),
							LocalDate.of(2026, 9, 8),
							3,
							"A steady start to the week",
							List.of("calm", "focused"),
							WALK_HABIT_ID
					),
					entry(
							ENTRY_IDS.get(1),
							LocalDate.of(2026, 9, 9),
							4,
							"A good day after a walk",
							List.of("positive", "energized"),
							WALK_HABIT_ID
					),
					entry(
							ENTRY_IDS.get(2),
							LocalDate.of(2026, 9, 10),
							5,
							"Grateful for a quiet evening",
							List.of("grateful", "calm"),
							JOURNAL_HABIT_ID
					)
			);

			entryRepository.saveAll(entries);

			for (DailyEntry entry : entries) {
				searchWriter.index(entry);
			}

			elasticsearchClient.refresh(indexManager.getIndexName());

			for (DailyEntry entry : entries) {
				String entryId = entry.getId();
				DailyEntrySearchDocument indexed = elasticsearchClient.get(
						indexManager.getIndexName(), entryId, DailyEntrySearchDocument.class);
				if (!DailyEntrySearchDocument.from(entry).equals(indexed)) {
					throw SearchInfrastructureUnavailableException.duringE2eVerification(
							new IllegalStateException("Search document verification failed for " + entryId)
					);
				}
			}

			return new E2eResetView(
					userId,
					habitRepository.findByUserId(userId).size(),
					entries.size(),
					"verified"
			);
		} catch (ElasticsearchTransportException | ElasticsearchException exception) {
			throw SearchInfrastructureUnavailableException.duringE2eIndexing(exception);
		}
	}

	private DailyEntry entry(
			String id,
			LocalDate date,
			int score,
			String note,
			List<String> tags,
			String habitId
	) {
		DailyEntry entry = new DailyEntry(userId, date);

		entry.setId(id);
		entry.setMood(new Mood(score, tags, note));
		entry.setHabits(List.of(new HabitLog(habitId, true, null)));

		Instant timestamp = Instant.parse(date + "T12:00:00Z");
		entry.setCreatedAt(timestamp);
		entry.setUpdatedAt(timestamp);

		return entry;
	}
}
