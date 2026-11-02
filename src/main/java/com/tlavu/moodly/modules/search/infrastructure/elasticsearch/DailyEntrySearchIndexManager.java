package com.tlavu.moodly.modules.search.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Owns lifecycle operations for the derived daily-entry search index. */
@Component
public class DailyEntrySearchIndexManager {
	private final ElasticsearchClient elasticsearchClient;

	@Getter
	private final String indexName;
	private final Resource mapping;

	public DailyEntrySearchIndexManager(
			ElasticsearchClient elasticsearchClient,
			@Value("${moodly.search.index.name}") String indexName,
			@Value("${moodly.search.index.mapping-resource}") Resource mapping
	) {
		this.elasticsearchClient = elasticsearchClient;
		this.indexName = indexName;
		this.mapping = mapping;
	}

	public void createIfMissing() throws IOException {
		if (
				!elasticsearchClient.indices()
						.exists(request -> request.index(indexName))
						.value()
		) {
			create(readMapping());
		}
	}

	public void recreate() throws IOException {
		byte[] mappingBytes = readMapping();
		if (elasticsearchClient.indices().exists(request -> request.index(indexName)).value()) {
			elasticsearchClient.indices().delete(request -> request.index(indexName));
		}

		create(mappingBytes);
	}

	private byte[] readMapping() {
		try (InputStream mappingStream = mapping.getInputStream()) {
			return mappingStream.readAllBytes();
		} catch (IOException exception) {
			throw new IllegalStateException("Could not read the configured Elasticsearch index mapping", exception);
		}
	}

	private void create(byte[] mappingBytes) throws IOException {
		elasticsearchClient.indices()
				.create(request -> request.index(indexName)
					.withJson(new ByteArrayInputStream(mappingBytes)));
	}
}
