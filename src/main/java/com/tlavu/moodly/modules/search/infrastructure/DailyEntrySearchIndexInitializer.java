package com.tlavu.moodly.modules.search.infrastructure;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Creates the derived daily-entry search index without changing an existing mapping. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
	name = "moodly.search.index.initialization-enabled",
	havingValue = "true",
	matchIfMissing = true
)
public class DailyEntrySearchIndexInitializer implements ApplicationRunner {

	public static final String INDEX_NAME = "daily_entries_search";
	private static final ClassPathResource MAPPING = new ClassPathResource("elasticsearch/daily_entries_search.json");

	private final ElasticsearchClient elasticsearchClient;

	@Override
	public void run(@NonNull ApplicationArguments args) throws Exception {
		if (elasticsearchClient.indices().exists(request -> request.index(INDEX_NAME)).value()) {
			return;
		}

		try (InputStream mapping = MAPPING.getInputStream()) {
			elasticsearchClient.indices().create(request -> request.index(INDEX_NAME).withJson(mapping));
		}
	}
}
