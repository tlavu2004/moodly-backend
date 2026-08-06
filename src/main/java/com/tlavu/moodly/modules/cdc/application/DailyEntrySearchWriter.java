package com.tlavu.moodly.modules.cdc.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.search.infrastructure.DailyEntrySearchIndexManager;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class DailyEntrySearchWriter {

	private final ElasticsearchClient elasticsearchClient;
	private final DailyEntrySearchIndexManager indexManager;

	public DailyEntrySearchWriter(ElasticsearchClient elasticsearchClient, DailyEntrySearchIndexManager indexManager) {
		this.elasticsearchClient = elasticsearchClient;
		this.indexManager = indexManager;
	}

	public void index(DailyEntry entry) throws IOException {
		index(entry.getId(), DailyEntrySearchDocument.from(entry));
	}

	public void index(String entryId, DailyEntrySearchDocument document) throws IOException {
		elasticsearchClient.index(request -> request
				.index(indexManager.getIndexName())
				.id(entryId)
				.document(document));
	}

	public void delete(String entryId) throws IOException {
		elasticsearchClient.delete(request -> request
				.index(indexManager.getIndexName())
				.id(entryId));
	}
}
