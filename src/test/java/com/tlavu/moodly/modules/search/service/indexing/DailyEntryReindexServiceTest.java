package com.tlavu.moodly.modules.search.service.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchIndexManager;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchWriter;
import com.tlavu.moodly.shared.error.exception.common.SearchInfrastructureUnavailableException;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class DailyEntryReindexServiceTest {

	@Mock
	private MongoTemplate mongoTemplate;
	@Mock
	private DailyEntrySearchWriter searchWriter;
	@Mock
	private DailyEntrySearchIndexManager indexManager;

	@Test
	void recreatesIndexAndIndexesEveryEntryFromTheCursor() throws Exception {
		DailyEntry first = new DailyEntry("user-1", LocalDate.of(2026, 8, 5));
		DailyEntry second = new DailyEntry("user-1", LocalDate.of(2026, 8, 6));
		DailyEntry third = new DailyEntry("user-1", LocalDate.of(2026, 8, 7));
		when(mongoTemplate.stream(any(Query.class), eq(DailyEntry.class)))
				.thenReturn(Stream.of(first, second, third));

		DailyEntryReindexService service = new DailyEntryReindexService(mongoTemplate, searchWriter, indexManager, 2);

		assertThat(service.reindex().indexedEntries()).isEqualTo(3);
		InOrder order = inOrder(indexManager, searchWriter);
		order.verify(indexManager).recreate();
		order.verify(searchWriter).index(first);
		order.verify(searchWriter).index(second);
		order.verify(searchWriter).index(third);
		verify(mongoTemplate).stream(any(Query.class), eq(DailyEntry.class));
	}

	@Test
	void returnsZeroWhenTheSourceCollectionIsEmpty() throws Exception {
		when(mongoTemplate.stream(any(Query.class), eq(DailyEntry.class))).thenReturn(Stream.empty());
		DailyEntryReindexService service = new DailyEntryReindexService(mongoTemplate, searchWriter, indexManager, 2);

		assertThat(service.reindex().indexedEntries()).isZero();
		verify(indexManager).recreate();
	}

	@Test
	void indexesACompleteSinglePage() throws Exception {
		DailyEntry entry = new DailyEntry("user-1", LocalDate.of(2026, 8, 6));
		when(mongoTemplate.stream(any(Query.class), eq(DailyEntry.class))).thenReturn(Stream.of(entry));
		DailyEntryReindexService service = new DailyEntryReindexService(mongoTemplate, searchWriter, indexManager, 2);

		assertThat(service.reindex().indexedEntries()).isEqualTo(1);
		verify(searchWriter).index(entry);
	}

	@Test
	void mapsElasticsearchIoFailureToSearchInfrastructureUnavailable() throws Exception {
		doThrow(new IOException("down")).when(indexManager).recreate();
		DailyEntryReindexService service = new DailyEntryReindexService(mongoTemplate, searchWriter, indexManager, 2);

		assertThatThrownBy(service::reindex)
				.isInstanceOf(SearchInfrastructureUnavailableException.class)
				.hasMessage("Elasticsearch reindex is unavailable")
				.hasCauseInstanceOf(IOException.class);
	}

	@Test
	void propagatesElasticsearchBadRequestDuringReindex() throws Exception {
		ElasticsearchException badRequest = org.mockito.Mockito.mock(ElasticsearchException.class);
		when(badRequest.status()).thenReturn(400);
		doThrow(badRequest).when(indexManager).recreate();
		DailyEntryReindexService service = new DailyEntryReindexService(mongoTemplate, searchWriter, indexManager, 2);

		assertThatThrownBy(service::reindex).isSameAs(badRequest);
	}
}
