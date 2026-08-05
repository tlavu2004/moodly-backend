package com.tlavu.moodly.modules.search.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.util.NamedValue;
import com.tlavu.moodly.modules.cdc.application.DailyEntrySearchDocument;
import com.tlavu.moodly.modules.search.infrastructure.DailyEntrySearchIndexManager;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EntrySearchService {

	private static final List<String> SEARCH_FIELDS = List.of("mood.note", "habits.note", "mood.tags");
	private static final List<NamedValue<HighlightField>> HIGHLIGHT_FIELDS = List.of(
			NamedValue.of("mood.note", new HighlightField.Builder().build()),
			NamedValue.of("habits.note", new HighlightField.Builder().build()),
			NamedValue.of("mood.tags", new HighlightField.Builder().build())
	);
	private static final int MAX_RESULTS = 50;

	private final ElasticsearchClient elasticsearchClient;

	public EntrySearchService(ElasticsearchClient elasticsearchClient) {
		this.elasticsearchClient = elasticsearchClient;
	}

	public List<EntrySearchResult> search(String userId, String query, LocalDate from, LocalDate to) {
		try {
			var response = elasticsearchClient.search(request -> request
					.index(DailyEntrySearchIndexManager.INDEX_NAME)
					.size(MAX_RESULTS)
					.query(searchQuery -> searchQuery.bool(bool -> {
						bool.must(match -> match.multiMatch(multiMatch -> multiMatch
								.query(query)
								.fields(SEARCH_FIELDS)));
						bool.filter(filter -> filter.term(term -> term
								.field("userId")
								.value(userId)));
						if (from != null || to != null) {
							bool.filter(filter -> filter.range(range -> range.date(date -> {
								date.field("date");
								if (from != null) {
									date.gte(from.toString());
								}
								if (to != null) {
									date.lte(to.toString());
								}
								return date;
							})));
						}
						return bool;
					}))
					.highlight(highlight -> highlight
							.preTags("<em>")
							.postTags("</em>")
							.fields(HIGHLIGHT_FIELDS)),
					DailyEntrySearchDocument.class);

			return response.hits().hits().stream()
					.filter(hit -> hit.source() != null)
					.map(hit -> new EntrySearchResult(
							hit.id(),
							hit.source().date(),
							Map.copyOf(hit.highlight())
					))
					.toList();
		} catch (IOException exception) {
			throw new IllegalStateException("Elasticsearch search is unavailable", exception);
		}
	}

	public record EntrySearchResult(String entryId, LocalDate date, Map<String, List<String>> highlights) {
	}
}
