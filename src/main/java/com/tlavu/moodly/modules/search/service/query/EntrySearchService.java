package com.tlavu.moodly.modules.search.service.query;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.util.NamedValue;
import com.tlavu.moodly.modules.search.model.elasticsearch.DailyEntrySearchDocument;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchIndexManager;
import com.tlavu.moodly.modules.search.service.input.EntrySearchInput;
import com.tlavu.moodly.modules.search.service.view.EntrySearchPageView;
import com.tlavu.moodly.modules.search.service.view.EntrySearchView;
import com.tlavu.moodly.modules.search.service.view.EntrySearchView.HighlightFragment;
import com.tlavu.moodly.modules.search.service.view.EntrySearchView.HighlightRange;
import com.tlavu.moodly.modules.search.validation.EntrySearchPagination;
import com.tlavu.moodly.shared.error.exception.common.SearchInfrastructureUnavailableException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;

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

	public EntrySearchPageView search(String userId, EntrySearchInput input) {
		String query = input.query();
		LocalDate from = input.from();
		LocalDate to = input.to();
		int page = input.page();
		int size = input.size();
		int offset = EntrySearchPagination.validatedOffset(page, size);
		try {
			var response = elasticsearchClient.search(request -> request
					.index(indexManager.getIndexName())
					.from(offset)
					.size(size)
					.trackTotalHits(track -> track.enabled(true))
					.sort(sort -> sort.score(score -> score.order(SortOrder.Desc)))
					.sort(sort -> sort.field(field -> field.field("date").order(SortOrder.Desc)))
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
						var highlights = normalizeHighlights(hit.highlight());
						return new EntrySearchView(hit.id(), hit.source().date(), highlights);
					})
					.toList();
			long total = response.hits().total() == null ? items.size() : response.hits().total().value();
			return new EntrySearchPageView(items, page, size, total);
		} catch (IOException | co.elastic.clients.elasticsearch._types.ElasticsearchException exception) {
			throw SearchInfrastructureUnavailableException.duringSearch(exception);
		}
	}

	private Map<String, List<HighlightFragment>> normalizeHighlights(Map<String, List<String>> raw) {
		if (raw == null || raw.isEmpty()) return Map.of();
		var normalized = new LinkedHashMap<String, List<HighlightFragment>>();
		raw.forEach((field, fragments) -> normalized.put(field, fragments.stream().map(this::plainFragment).toList()));
		return Map.copyOf(normalized);
	}

	private HighlightFragment plainFragment(String marked) {
		var text = new StringBuilder();
		var ranges = new ArrayList<HighlightRange>();
		int cursor = 0;
		while (cursor < marked.length()) {
			int startTag = marked.indexOf("<em>", cursor);
			if (startTag < 0) {
				text.append(marked, cursor, marked.length());
				break;
			}
			text.append(marked, cursor, startTag);
			int endTag = marked.indexOf("</em>", startTag + 4);
			if (endTag < 0) {
				text.append(marked, startTag, marked.length());
				break;
			}
			int start = text.length();
			text.append(marked, startTag + 4, endTag);
			ranges.add(new HighlightRange(start, text.length()));
			cursor = endTag + 5;
		}
		return new HighlightFragment(text.toString(), List.copyOf(ranges));
	}
}
