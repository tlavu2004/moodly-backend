package com.tlavu.moodly.modules.search.infrastructure;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Owns lifecycle operations for the derived daily-entry search index. */
@Component
public class DailyEntrySearchIndexManager {

	public static final String INDEX_NAME = "daily_entries_search";
	private static final ClassPathResource MAPPING = new ClassPathResource("elasticsearch/daily_entries_search.json");

	private final ElasticsearchClient elasticsearchClient;

	public DailyEntrySearchIndexManager(ElasticsearchClient elasticsearchClient) {
		this.elasticsearchClient = elasticsearchClient;
	}

	public void createIfMissing() throws IOException {
		if (!elasticsearchClient.indices().exists(request -> request.index(INDEX_NAME)).value()) {
			create();
		}
	}

	public void recreate() throws IOException {
		if (elasticsearchClient.indices().exists(request -> request.index(INDEX_NAME)).value()) {
			elasticsearchClient.indices().delete(request -> request.index(INDEX_NAME));
		}
		create();
	}

	private void create() throws IOException {
		try (InputStream mapping = MAPPING.getInputStream()) {
			elasticsearchClient.indices().create(request -> request.index(INDEX_NAME).withJson(mapping));
		}
	}
}
