package com.tlavu.moodly.modules.e2e.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tlavu.moodly.modules.cdc.service.DailyEntrySearchWriter;
import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.entries.repository.DailyEntryRepository;
import com.tlavu.moodly.modules.habits.model.Habit;
import com.tlavu.moodly.modules.habits.model.TargetFrequency;
import com.tlavu.moodly.modules.habits.repository.HabitRepository;
import com.tlavu.moodly.modules.search.repository.DailyEntrySearchIndexManager;
import com.tlavu.moodly.shared.error.exception.SearchInfrastructureUnavailableException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Resets only the fixed synthetic account reserved for browser E2E tests. */
@Service
public class E2eUserResetService {

	public static final String DEFAULT_USER_ID = "synthetic|moodly-playwright-e2e";
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
			@Value("${moodly.e2e.reset.user-id:synthetic|moodly-playwright-e2e}") String userId
	) {
		this.habitRepository = habitRepository;
		this.entryRepository = entryRepository;
		this.searchWriter = searchWriter;
		this.elasticsearchClient = elasticsearchClient;
		this.indexManager = indexManager;
		this.userId = userId;
	}

	public synchronized ResetResult reset() {
		try {
			// Remove only this synthetic subject's old projection, including any stale documents.
			elasticsearchClient.deleteByQuery(request -> request
					.index(indexManager.getIndexName())
					.query(query -> query.term(term -> term.field("userId").value(userId))));
			elasticsearchClient.indices().refresh(request -> request.index(indexManager.getIndexName()));

			entryRepository.deleteByUserId(userId);
			habitRepository.deleteAll(habitRepository.findByUserId(userId));

			var walk = new Habit(WALK_HABIT_ID, userId, "Daily walk", "walk", TargetFrequency.DAILY, true);
			var journal = new Habit(JOURNAL_HABIT_ID, userId, "Evening journal", "book", TargetFrequency.DAILY, true);
			habitRepository.saveAll(List.of(walk, journal));

			var entries = List.of(
					entry(ENTRY_IDS.get(0), LocalDate.of(2026, 9, 8), 3, "A steady start to the week", List.of("calm", "focused"), WALK_HABIT_ID, true),
					entry(ENTRY_IDS.get(1), LocalDate.of(2026, 9, 9), 4, "A good day after a walk", List.of("positive", "energized"), WALK_HABIT_ID, true),
					entry(ENTRY_IDS.get(2), LocalDate.of(2026, 9, 10), 5, "Grateful for a quiet evening", List.of("grateful", "calm"), JOURNAL_HABIT_ID, true)
			);
			entryRepository.saveAll(entries);
			for (var entry : entries) {
				searchWriter.index(entry);
			}
			elasticsearchClient.indices().refresh(request -> request.index(indexManager.getIndexName()));

			for (var entryId : ENTRY_IDS) {
				var indexed = elasticsearchClient.get(request -> request.index(indexManager.getIndexName()).id(entryId),
						com.tlavu.moodly.modules.cdc.service.DailyEntrySearchDocument.class);
				if (!indexed.found() || !userId.equals(indexed.source().userId())) {
					throw new SearchInfrastructureUnavailableException(
							"E2E reset search indexing could not be verified",
							new IllegalStateException("Search document verification failed for " + entryId));
				}
			}
			return new ResetResult(userId, habitRepository.findByUserId(userId).size(), entries.size(), "verified");
		} catch (IOException | co.elastic.clients.elasticsearch._types.ElasticsearchException exception) {
			throw new SearchInfrastructureUnavailableException("E2E reset search indexing is unavailable", exception);
		}
	}

	private DailyEntry entry(String id, LocalDate date, int score, String note, List<String> tags, String habitId, boolean done) {
		var entry = new DailyEntry(userId, date);
		entry.setId(id);
		entry.setMood(new DailyEntry.Mood(score, tags, note));
		entry.setHabits(List.of(new DailyEntry.HabitLog(habitId, done, null)));
		var timestamp = Instant.parse(date + "T12:00:00Z");
		entry.setCreatedAt(timestamp);
		entry.setUpdatedAt(timestamp);
		return entry;
	}

	public record ResetResult(String userId, int habits, int entries, String searchIndexing) {
	}
}
