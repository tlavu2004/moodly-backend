package com.tlavu.moodly.modules.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.util.ObjectBuilder;
import com.tlavu.moodly.modules.cdc.application.DailyEntrySearchDocument;
import com.tlavu.moodly.modules.search.infrastructure.DailyEntrySearchIndexManager;
import com.tlavu.moodly.shared.application.exception.SearchInfrastructureUnavailableException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntrySearchServiceTest {

	@Mock private ElasticsearchClient elasticsearchClient;
	@Mock private DailyEntrySearchIndexManager indexManager;
	@Mock private SearchResponse<DailyEntrySearchDocument> response;
	@Mock private HitsMetadata<DailyEntrySearchDocument> hits;
	@Mock private Hit<DailyEntrySearchDocument> hit;
	@Captor
	private ArgumentCaptor<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>> searchRequestBuilder;

	@Test
	void scopesSearchToUserAndMapsMissingHighlightsToAnEmptyMap() throws Exception {
		when(indexManager.getIndexName()).thenReturn("entries-test");
		when(elasticsearchClient.search(anySearchRequestBuilder(), eq(DailyEntrySearchDocument.class))).thenReturn(response);
		when(response.hits()).thenReturn(hits);
		when(hits.hits()).thenReturn(List.of(hit));
		when(hit.id()).thenReturn("entry-1");
		when(hit.source()).thenReturn(new DailyEntrySearchDocument("user-1", LocalDate.of(2026, 8, 6), null, List.of()));
		when(hit.highlight()).thenReturn(null);
		var service = new EntrySearchService(elasticsearchClient, indexManager);

		var result = service.search("user-1", "tired", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

		assertThat(result).singleElement().satisfies(searchResult -> {
			assertThat(searchResult.entryId()).isEqualTo("entry-1");
			assertThat(searchResult.highlights()).isEmpty();
		});
		verify(elasticsearchClient).search(searchRequestBuilder.capture(), eq(DailyEntrySearchDocument.class));
		var builtRequest = searchRequestBuilder.getValue().apply(new SearchRequest.Builder()).build();
		assertThat(builtRequest.index()).containsExactly("entries-test");
		assertThat(builtRequest.toString()).contains("user-1", "2026-08-01", "2026-08-31", "tired");
	}

	@Test
	void mapsElasticsearchIoFailureToUnavailableError() throws Exception {
		doThrow(new IOException("down"))
				.when(elasticsearchClient).search(anySearchRequestBuilder(), eq(DailyEntrySearchDocument.class));
		var service = new EntrySearchService(elasticsearchClient, indexManager);

		assertThatThrownBy(() -> service.search("user-1", "tired", null, null))
				.isInstanceOf(SearchInfrastructureUnavailableException.class)
				.hasMessage("Elasticsearch search is unavailable")
				.hasCauseInstanceOf(IOException.class);
	}

	private Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> anySearchRequestBuilder() {
		return org.mockito.ArgumentMatchers.any();
	}
}
