package com.tlavu.moodly.modules.search.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.util.NamedValue;
import com.tlavu.moodly.modules.cdc.application.DailyEntrySearchDocument;
import com.tlavu.moodly.modules.search.infrastructure.DailyEntrySearchIndexManager;
import com.tlavu.moodly.shared.application.exception.SearchInfrastructureUnavailableException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.tlavu.moodly.shared.presentation.dto.response.PageResponse;

@Service
public class EntrySearchService {

	private static final List<String> SEARCH_FIELDS = List.of("mood.note", "habits.note", "mood.tags");
	private static final List<NamedValue<HighlightField>> HIGHLIGHT_FIELDS = List.of(
			NamedValue.of("mood.note", new HighlightField.Builder().build()),
			NamedValue.of("habits.note", new HighlightField.Builder().build()),
			NamedValue.of("mood.tags", new HighlightField.Builder().build())
	);
	private final ElasticsearchClient elasticsearchClient;
	private final DailyEntrySearchIndexManager indexManager;

	public EntrySearchService(ElasticsearchClient elasticsearchClient, DailyEntrySearchIndexManager indexManager) {
		this.elasticsearchClient = elasticsearchClient;
		this.indexManager = indexManager;
	}

	public List<EntrySearchResult> search(String userId, String query, LocalDate from, LocalDate to) {
		return search(userId, query, from, to, 0, 50).items();
	}

	public PageResponse<EntrySearchResult> search(String userId, String query, LocalDate from, LocalDate to, int page, int size) {
		try {
			var response = elasticsearchClient.search(request -> request
					.index(indexManager.getIndexName())
					.from(page * size)
					.size(size)
					.trackTotalHits(track -> track.enabled(true))
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

			var items = response.hits().hits().stream()
					.filter(hit -> hit.source() != null)
					.map(hit -> {
						var highlights = hit.highlight() == null
								? Map.<String, List<String>>of()
								: Map.copyOf(hit.highlight());
						return new EntrySearchResult(hit.id(), hit.source().date(), highlights);
					})
					.toList();
			long total = response.hits().total() == null ? items.size() : response.hits().total().value();
			return PageResponse.of(items, page, size, total);
		} catch (IOException exception) {
			throw new SearchInfrastructureUnavailableException("Elasticsearch search is unavailable", exception);
		}
	}

	public record EntrySearchResult(String entryId, LocalDate date, Map<String, List<String>> highlights) {
	}
}
