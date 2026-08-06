package com.tlavu.moodly.support;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tlavu.moodly.modules.cdc.domain.CdcDeadLetter;
import com.tlavu.moodly.modules.cdc.domain.CdcResumeToken;
import com.tlavu.moodly.modules.cdc.infrastructure.CdcDeadLetterRepository;
import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.entries.infrastructure.DailyEntryRepository;
import com.tlavu.moodly.modules.search.infrastructure.DailyEntrySearchIndexManager;
import java.util.UUID;
import org.springframework.data.mongodb.core.MongoTemplate;

/** Shared seed, cleanup, and eventual-consistency helpers for CDC integration tests. */
@SuppressWarnings("unused") // Individual scenarios consume this shared test-support API incrementally.
public class CdcSearchTestSupport {

	private final DailyEntryRepository dailyEntryRepository;
	private final CdcDeadLetterRepository deadLetterRepository;
	private final MongoTemplate mongoTemplate;
	private final ElasticsearchClient elasticsearchClient;
	private final DailyEntrySearchIndexManager indexManager;

	public CdcSearchTestSupport(
			DailyEntryRepository dailyEntryRepository,
			CdcDeadLetterRepository deadLetterRepository,
			MongoTemplate mongoTemplate,
			ElasticsearchClient elasticsearchClient,
			DailyEntrySearchIndexManager indexManager
	) {
		this.dailyEntryRepository = dailyEntryRepository;
		this.deadLetterRepository = deadLetterRepository;
		this.mongoTemplate = mongoTemplate;
		this.elasticsearchClient = elasticsearchClient;
		this.indexManager = indexManager;
	}

	public String newUserId() {
		return "cdc-test-" + UUID.randomUUID();
	}

	public DailyEntry save(DailyEntry entry) {
		return dailyEntryRepository.save(entry);
	}

	public void awaitIndexed(String entryId) throws Exception {
		AsyncTestAwaiter.until(
				"Elasticsearch document " + entryId + " to be indexed",
				() -> elasticsearchClient.get(request -> request.index(indexManager.getIndexName()).id(entryId), DailyEntry.class).found(),
				() -> describeDocument(entryId)
		);
	}

	public void awaitRemoved(String entryId) throws Exception {
		AsyncTestAwaiter.until(
				"Elasticsearch document " + entryId + " to be removed",
				() -> !elasticsearchClient.get(request -> request.index(indexManager.getIndexName()).id(entryId), DailyEntry.class).found(),
				() -> describeDocument(entryId)
		);
	}

	public void awaitDeadLetter(String eventId) throws Exception {
		AsyncTestAwaiter.until(
				"CDC dead letter " + eventId,
				() -> deadLetterRepository.findAll().stream().map(CdcDeadLetter::getEventId).anyMatch(eventId::equals),
				() -> "dead-letter event IDs=" + deadLetterRepository.findAll().stream().map(CdcDeadLetter::getEventId).toList()
		);
	}

	public void clearState() throws Exception {
		dailyEntryRepository.deleteAll();
		deadLetterRepository.deleteAll();
		mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), CdcResumeToken.class);
		indexManager.recreate();
	}

	private String describeDocument(String entryId) {
		try {
			return "Elasticsearch document present=" + elasticsearchClient
					.get(request -> request.index(indexManager.getIndexName()).id(entryId), DailyEntry.class)
					.found();
		} catch (Exception exception) {
			return "Elasticsearch lookup failed: " + exception.getMessage();
		}
	}
}
