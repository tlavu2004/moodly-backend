package com.tlavu.moodly.modules.search.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.util.NamedValue;
import com.tlavu.moodly.modules.search.model.elasticsearch.DailyEntrySearchDocument;
import com.tlavu.moodly.modules.search.service.port.EntrySearchQueryPort;
import com.tlavu.moodly.modules.search.service.input.EntrySearchInput;
import com.tlavu.moodly.modules.search.service.view.EntrySearchPageView;
import com.tlavu.moodly.modules.search.service.view.EntrySearchView;
import com.tlavu.moodly.modules.search.service.view.HighlightFragmentView;
import com.tlavu.moodly.modules.search.service.view.HighlightRangeView;
import com.tlavu.moodly.shared.error.exception.common.SearchInfrastructureUnavailableException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ElasticsearchEntrySearchAdapter implements EntrySearchQueryPort {

	private static final List<String> SEARCH_FIELDS = List.of("mood.note", "habits.note", "mood.tags");
	private static final List<NamedValue<HighlightField>> HIGHLIGHT_FIELDS = List.of(
			NamedValue.of("mood.note", new HighlightField.Builder().build()),
			NamedValue.of("habits.note", new HighlightField.Builder().build()),
			NamedValue.of("mood.tags", new HighlightField.Builder().build())
	);
	private final ElasticsearchClient elasticsearchClient;
	private final DailyEntrySearchIndexManager indexManager;

	@Override
	public EntrySearchPageView search(String userId, EntrySearchInput input, int offset) {
		String query = input.query();
		LocalDate from = input.from();
		LocalDate to = input.to();
		int page = input.page();
		int size = input.size();
		try {
			SearchResponse<DailyEntrySearchDocument> response = elasticsearchClient.search(request -> request
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

			List<EntrySearchView> items = response.hits().hits().stream()
					.filter(hit -> hit.source() != null)
					.map(hit -> {
						Map<String, List<HighlightFragmentView>> highlights = normalizeHighlights(hit.highlight());
						return new EntrySearchView(hit.id(), hit.source().date(), highlights);
					})
					.toList();
			long total = response.hits().total() == null ? items.size() : response.hits().total().value();
			return new EntrySearchPageView(items, page, size, total);
		} catch (IOException exception) {
			throw SearchInfrastructureUnavailableException.duringSearch(exception);
		} catch (ElasticsearchException exception) {
			if (ElasticsearchFailureClassifier.isUnavailable(exception)) {
				throw SearchInfrastructureUnavailableException.duringSearch(exception);
			}
			throw exception;
		}
	}

	private Map<String, List<HighlightFragmentView>> normalizeHighlights(Map<String, List<String>> raw) {
		if (raw == null || raw.isEmpty()) return Map.of();
		Map<String, List<HighlightFragmentView>> normalized = new LinkedHashMap<>();
		raw.forEach((field, fragments) -> normalized.put(field, fragments.stream().map(this::plainFragment).toList()));
		return Map.copyOf(normalized);
	}

	private HighlightFragmentView plainFragment(String marked) {
		StringBuilder text = new StringBuilder();
		List<HighlightRangeView> ranges = new ArrayList<>();
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
			ranges.add(new HighlightRangeView(start, text.length()));
			cursor = endTag + 5;
		}
		return new HighlightFragmentView(text.toString(), List.copyOf(ranges));
	}
}
