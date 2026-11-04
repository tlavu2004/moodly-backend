package com.tlavu.moodly.modules.search.infrastructure.elasticsearch;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.search.model.elasticsearch.DailyEntrySearchDocument;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyEntrySearchWriterTest {

	@Mock
	private ElasticsearchClient elasticsearchClient;
	@Mock
	private DailyEntrySearchIndexManager indexManager;

	@Test
	void indexesWithConfiguredIndexAndDeterministicEntryId() throws Exception {
		when(indexManager.getIndexName()).thenReturn("entries-test");
		DailyEntrySearchWriter writer = new DailyEntrySearchWriter(elasticsearchClient, indexManager);
		DailyEntrySearchDocument document = new DailyEntrySearchDocument("user-1", LocalDate.of(2026, 8, 6), null, java.util.List.of());

		writer.index("entry-1", document);

		verify(elasticsearchClient).index("entries-test", "entry-1", document);
	}

	@Test
	void deletesFromConfiguredIndexUsingEntryId() throws Exception {
		when(indexManager.getIndexName()).thenReturn("entries-test");
		DailyEntrySearchWriter writer = new DailyEntrySearchWriter(elasticsearchClient, indexManager);

		writer.delete("entry-1");

		verify(elasticsearchClient).delete("entries-test", "entry-1");
	}
}
