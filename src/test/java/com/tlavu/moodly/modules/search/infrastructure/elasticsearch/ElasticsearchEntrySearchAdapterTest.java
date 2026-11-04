package com.tlavu.moodly.modules.search.infrastructure.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tlavu.moodly.modules.search.service.input.EntrySearchInput;
import com.tlavu.moodly.modules.search.service.view.EntrySearchView;
import com.tlavu.moodly.modules.search.service.view.HighlightRangeView;
import com.tlavu.moodly.shared.error.exception.common.SearchInfrastructureUnavailableException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class ElasticsearchEntrySearchAdapterTest {

	@Mock private ElasticsearchClient elasticsearchClient;
	@Mock private DailyEntrySearchIndexManager indexManager;
	private final tools.jackson.databind.json.JsonMapper json = new tools.jackson.databind.json.JsonMapper();
	@Captor private ArgumentCaptor<Map<String, Object>> searchRequest;

	@Test
	void scopesSearchToUserAndNormalizesHighlights() throws Exception {
		when(indexManager.getIndexName()).thenReturn("entries-test");
		when(elasticsearchClient.search(eq("entries-test"), org.mockito.ArgumentMatchers.anyMap()))
				.thenReturn(json.readTree("""
						{"hits":{"hits":[{"_id":"entry-1","_source":{"date":"2026-08-06"},"highlight":{
						  "mood.tags":["<em>tired</em>"],"mood.note":["I felt <em>tired</em>."],"habits.note":["<em>tired</em> habit"]
						}}]}}
						"""));
		ElasticsearchEntrySearchAdapter adapter = new ElasticsearchEntrySearchAdapter(elasticsearchClient, indexManager);

		List<EntrySearchView> result = adapter.search("user-1", new EntrySearchInput(
				"tired", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 0, 50
		), 0).items();

		assertThat(result).singleElement().satisfies(searchResult -> {
			assertThat(searchResult.entryId()).isEqualTo("entry-1");
			assertThat(searchResult.highlights().keySet())
					.containsExactly("habits.note", "mood.note", "mood.tags");
			assertThatThrownBy(() -> searchResult.highlights().clear())
					.isInstanceOf(UnsupportedOperationException.class);
			assertThat(searchResult.highlights().get("mood.note")).singleElement().satisfies(fragment -> {
				assertThat(fragment.text()).isEqualTo("I felt tired.");
				assertThat(fragment.ranges()).containsExactly(new HighlightRangeView(7, 12));
			});
		});
		verify(elasticsearchClient).search(org.mockito.ArgumentMatchers.any(), searchRequest.capture());
		String builtRequest = json.writeValueAsString(searchRequest.getValue());
		assertThat(builtRequest.toString()).contains("user-1", "2026-08-01", "2026-08-31", "tired", "_score", "date");
	}

	@Test
	void mapsElasticsearchIoFailureToUnavailableError() throws Exception {
		doThrow(new IOException("down"))
				.when(elasticsearchClient).search(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap());
		ElasticsearchEntrySearchAdapter adapter = new ElasticsearchEntrySearchAdapter(elasticsearchClient, indexManager);

		assertThatThrownBy(() -> adapter.search("user-1", new EntrySearchInput("tired", null, null, 0, 50), 0))
				.isInstanceOf(SearchInfrastructureUnavailableException.class)
				.hasMessage("Elasticsearch search is unavailable")
				.hasCauseInstanceOf(IOException.class);
	}

	@Test
	void rejectsAHitWithoutSourceInsteadOfReturningAnIncompletePage() throws Exception {
		when(elasticsearchClient.search(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap()))
				.thenReturn(json.readTree("{\"hits\":{\"hits\":[{\"_id\":\"entry-1\"}]}}"));
		ElasticsearchEntrySearchAdapter adapter = new ElasticsearchEntrySearchAdapter(elasticsearchClient, indexManager);
		Logger logger = (Logger) LoggerFactory.getLogger(ElasticsearchEntrySearchAdapter.class);
		ListAppender<ILoggingEvent> events = new ListAppender<>();
		events.start();
		logger.addAppender(events);

		try {
			assertThatThrownBy(() -> adapter.search("user-1", new EntrySearchInput("private-query", null, null, 0, 50), 0))
					.isInstanceOf(IllegalStateException.class)
					.hasMessage("Elasticsearch hit entry-1 has no source");
			assertThat(events.list).singleElement().satisfies(event -> {
				assertThat(event.getFormattedMessage()).contains("hitId=entry-1", "rebuild the index")
						.doesNotContain("private-query", "user-1");
				assertThat(event.getThrowableProxy()).isNull();
			});
		} finally {
			logger.detachAppender(events);
			events.stop();
		}
	}

	@Test
	void propagatesElasticsearchBadRequestAsAnInternalFailure() throws Exception {
		ElasticsearchException badRequest = org.mockito.Mockito.mock(ElasticsearchException.class);
		when(badRequest.status()).thenReturn(400);
		doThrow(badRequest)
				.when(elasticsearchClient).search(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap());
		ElasticsearchEntrySearchAdapter adapter = new ElasticsearchEntrySearchAdapter(elasticsearchClient, indexManager);

		assertThatThrownBy(() -> adapter.search("user-1", new EntrySearchInput("tired", null, null, 0, 50), 0))
				.isSameAs(badRequest);
	}

	@Test
	void mapsElasticsearchServerFailureToUnavailableError() throws Exception {
		ElasticsearchException serverFailure = org.mockito.Mockito.mock(ElasticsearchException.class);
		when(serverFailure.status()).thenReturn(503);
		doThrow(serverFailure)
				.when(elasticsearchClient).search(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap());
		ElasticsearchEntrySearchAdapter adapter = new ElasticsearchEntrySearchAdapter(elasticsearchClient, indexManager);

		assertThatThrownBy(() -> adapter.search("user-1", new EntrySearchInput("tired", null, null, 0, 50), 0))
				.isInstanceOf(SearchInfrastructureUnavailableException.class)
				.hasCause(serverFailure);
	}

	@Test
	void appliesOnlyTheFromDateBoundWhenToIsAbsent() throws Exception {
		stubEmptyResponse();
		ElasticsearchEntrySearchAdapter adapter = new ElasticsearchEntrySearchAdapter(elasticsearchClient, indexManager);

		adapter.search("user-1", new EntrySearchInput("tired", LocalDate.of(2026, 8, 1), null, 0, 50), 0);

		verify(elasticsearchClient).search(org.mockito.ArgumentMatchers.any(), searchRequest.capture());
		String request = json.writeValueAsString(searchRequest.getValue());
		assertThat(request).contains("2026-08-01", "\"gte\"").doesNotContain("\"lte\"");
	}

	@Test
	void appliesOnlyTheToDateBoundWhenFromIsAbsent() throws Exception {
		stubEmptyResponse();
		ElasticsearchEntrySearchAdapter adapter = new ElasticsearchEntrySearchAdapter(elasticsearchClient, indexManager);

		adapter.search("user-1", new EntrySearchInput("tired", null, LocalDate.of(2026, 8, 31), 0, 50), 0);

		verify(elasticsearchClient).search(org.mockito.ArgumentMatchers.any(), searchRequest.capture());
		String request = json.writeValueAsString(searchRequest.getValue());
		assertThat(request).contains("2026-08-31", "\"lte\"").doesNotContain("\"gte\"");
	}

	@Test
	void calculatesTheLargestSupportedOffsetWithoutOverflow() throws Exception {
		stubEmptyResponse();
		ElasticsearchEntrySearchAdapter adapter = new ElasticsearchEntrySearchAdapter(elasticsearchClient, indexManager);

		adapter.search("user-1", new EntrySearchInput("tired", null, null, 99, 100), 9_900);

		verify(elasticsearchClient).search(org.mockito.ArgumentMatchers.any(), searchRequest.capture());
		Map<String, Object> request = searchRequest.getValue();
		assertThat(request.get("from")).isEqualTo(9_900);
		assertThat(request.get("size")).isEqualTo(100);
	}

	private void stubEmptyResponse() throws Exception {
		when(elasticsearchClient.search(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap()))
				.thenReturn(json.readTree("{\"hits\":{\"hits\":[]}}"));
	}
}
