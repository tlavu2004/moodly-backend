package com.tlavu.moodly.modules.search.infrastructure.elasticsearch;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.search.model.elasticsearch.DailyEntrySearchDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyEntrySearchWriter {
	private final ElasticsearchClient elasticsearchClient;
	private final DailyEntrySearchIndexManager indexManager;

	public void index(DailyEntry entry) {
		index(entry.getId(), DailyEntrySearchDocument.from(entry));
	}

	public void index(String entryId, DailyEntrySearchDocument document) {
		elasticsearchClient.index(indexManager.getIndexName(), entryId, document);
	}

	public void delete(String entryId) {
		elasticsearchClient.delete(indexManager.getIndexName(), entryId);
	}
}
