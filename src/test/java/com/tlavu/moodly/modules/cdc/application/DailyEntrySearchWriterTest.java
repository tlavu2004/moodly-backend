package com.tlavu.moodly.modules.cdc.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.util.ObjectBuilder;
import com.tlavu.moodly.modules.search.infrastructure.DailyEntrySearchIndexManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.function.Function;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"}) // Elasticsearch client request lambdas are captured by erased runtime type.
class DailyEntrySearchWriterTest {

	@Mock
	private ElasticsearchClient elasticsearchClient;
	@Mock
	private DailyEntrySearchIndexManager indexManager;

	@Test
	void indexesWithConfiguredIndexAndDeterministicEntryId() throws Exception {
		when(indexManager.getIndexName()).thenReturn("entries-test");
		var writer = new DailyEntrySearchWriter(elasticsearchClient, indexManager);
		var document = new DailyEntrySearchDocument("user-1", LocalDate.of(2026, 8, 6), null, java.util.List.of());

		writer.index("entry-1", document);

		@SuppressWarnings("rawtypes")
		ArgumentCaptor<Function> request = ArgumentCaptor.forClass((Class) Function.class);
		verify(elasticsearchClient).index(request.capture());
		var builtRequest = buildIndexRequest(request.getValue());
		assertThat(builtRequest.index()).isEqualTo("entries-test");
		assertThat(builtRequest.id()).isEqualTo("entry-1");
		assertThat(builtRequest.document()).isEqualTo(document);
	}

	@Test
	void deletesFromConfiguredIndexUsingEntryId() throws Exception {
		when(indexManager.getIndexName()).thenReturn("entries-test");
		var writer = new DailyEntrySearchWriter(elasticsearchClient, indexManager);

		writer.delete("entry-1");

		@SuppressWarnings("rawtypes")
		ArgumentCaptor<Function> request = ArgumentCaptor.forClass((Class) Function.class);
		verify(elasticsearchClient).delete(request.capture());
		var builtRequest = buildDeleteRequest(request.getValue());
		assertThat(builtRequest.index()).isEqualTo("entries-test");
		assertThat(builtRequest.id()).isEqualTo("entry-1");
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private IndexRequest<?> buildIndexRequest(Function requestBuilder) {
		return ((ObjectBuilder<IndexRequest<?>>) requestBuilder.apply(new IndexRequest.Builder<>())).build();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private DeleteRequest buildDeleteRequest(Function requestBuilder) {
		return ((ObjectBuilder<DeleteRequest>) requestBuilder.apply(new DeleteRequest.Builder())).build();
	}
}
