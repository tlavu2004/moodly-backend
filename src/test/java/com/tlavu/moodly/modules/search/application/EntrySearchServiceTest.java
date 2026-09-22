package com.tlavu.moodly.modules.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
		when(hit.highlight()).thenReturn(java.util.Map.of("mood.note", List.of("I felt <em>tired</em>.")));
		var service = new EntrySearchService(elasticsearchClient, indexManager);

		var result = service.search("user-1", "tired", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

		assertThat(result).singleElement().satisfies(searchResult -> {
			assertThat(searchResult.entryId()).isEqualTo("entry-1");
			assertThat(searchResult.highlights().get("mood.note")).singleElement().satisfies(fragment -> {
				assertThat(fragment.text()).isEqualTo("I felt tired.");
				assertThat(fragment.ranges()).containsExactly(new EntrySearchService.HighlightRange(7, 12));
			});
		});
		verify(elasticsearchClient).search(searchRequestBuilder.capture(), eq(DailyEntrySearchDocument.class));
		var builtRequest = searchRequestBuilder.getValue().apply(new SearchRequest.Builder()).build();
		assertThat(builtRequest.index()).containsExactly("entries-test");
		assertThat(builtRequest.toString()).contains("user-1", "2026-08-01", "2026-08-31", "tired", "_score", "date");
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

	@Test
	void appliesOnlyTheFromDateBoundWhenToIsAbsent() throws Exception {
		stubEmptyResponse();
		var service = new EntrySearchService(elasticsearchClient, indexManager);

		service.search("user-1", "tired", LocalDate.of(2026, 8, 1), null);

		verify(elasticsearchClient).search(searchRequestBuilder.capture(), eq(DailyEntrySearchDocument.class));
		var request = searchRequestBuilder.getValue().apply(new SearchRequest.Builder()).build().toString();
		assertThat(request).contains("2026-08-01", "\"gte\"").doesNotContain("\"lte\"");
	}

	@Test
	void appliesOnlyTheToDateBoundWhenFromIsAbsent() throws Exception {
		stubEmptyResponse();
		var service = new EntrySearchService(elasticsearchClient, indexManager);

		service.search("user-1", "tired", null, LocalDate.of(2026, 8, 31));

		verify(elasticsearchClient).search(searchRequestBuilder.capture(), eq(DailyEntrySearchDocument.class));
		var request = searchRequestBuilder.getValue().apply(new SearchRequest.Builder()).build().toString();
		assertThat(request).contains("2026-08-31", "\"lte\"").doesNotContain("\"gte\"");
	}

	@Test
	void calculatesTheLargestSupportedOffsetWithoutOverflow() throws Exception {
		stubEmptyResponse();
		var service = new EntrySearchService(elasticsearchClient, indexManager);

		service.search("user-1", "tired", null, null, 99, 100);

		verify(elasticsearchClient).search(searchRequestBuilder.capture(), eq(DailyEntrySearchDocument.class));
		var request = searchRequestBuilder.getValue().apply(new SearchRequest.Builder()).build();
		assertThat(request.from()).isEqualTo(9_900);
		assertThat(request.size()).isEqualTo(100);
	}

	@Test
	void rejectsAnOverflowingOrUnsupportedOffsetBeforeCallingElasticsearch() {
		var service = new EntrySearchService(elasticsearchClient, indexManager);

		assertThatThrownBy(() -> service.search("user-1", "tired", null, null, Integer.MAX_VALUE, 100))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("result window");
		verifyNoInteractions(elasticsearchClient, indexManager);
	}

	private void stubEmptyResponse() throws Exception {
		when(elasticsearchClient.search(anySearchRequestBuilder(), eq(DailyEntrySearchDocument.class))).thenReturn(response);
		when(response.hits()).thenReturn(hits);
		when(hits.hits()).thenReturn(List.of());
	}

	private Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> anySearchRequestBuilder() {
		return org.mockito.ArgumentMatchers.any();
	}
}
