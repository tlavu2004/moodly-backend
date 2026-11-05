package com.tlavu.moodly.modules.search.infrastructure.elasticsearch;

import com.tlavu.moodly.modules.search.exception.SearchIndexMappingException;

import java.nio.charset.StandardCharsets;
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

	public boolean exists() {
		return elasticsearchClient.exists(indexName);
	}

	public void createIfMissing() {
		if (!exists()) {
			create(readMapping());
		}
	}

	public void recreate() {
		byte[] mappingBytes = readMapping();
		if (elasticsearchClient.exists(indexName)) {
			elasticsearchClient.deleteIndex(indexName);
		}
		create(mappingBytes);
	}

	private byte[] readMapping() {
		try (InputStream mappingStream = mapping.getInputStream()) {
			return mappingStream.readAllBytes();
		} catch (IOException exception) {
			throw new SearchIndexMappingException("Could not read the configured Elasticsearch index mapping", exception);
		}
	}

	private void create(byte[] mappingBytes) {
		elasticsearchClient.createIndex(
				indexName,
				new String(mappingBytes, StandardCharsets.UTF_8)
		);
	}
}
