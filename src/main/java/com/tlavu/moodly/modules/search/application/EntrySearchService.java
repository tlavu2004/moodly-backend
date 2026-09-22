package com.tlavu.moodly.modules.search.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.util.NamedValue;
import com.tlavu.moodly.modules.cdc.application.DailyEntrySearchDocument;
import com.tlavu.moodly.modules.search.infrastructure.DailyEntrySearchIndexManager;
import com.tlavu.moodly.shared.application.exception.SearchInfrastructureUnavailableException;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Service;
import com.tlavu.moodly.shared.presentation.dto.response.PageResponse;

@Service
public class EntrySearchService {

	public static final int MAX_RESULT_WINDOW = 10_000;
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
		int offset = validatedOffset(page, size);
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
						return new EntrySearchResult(hit.id(), hit.source().date(), highlights);
					})
					.toList();
			long total = response.hits().total() == null ? items.size() : response.hits().total().value();
			return PageResponse.of(items, page, size, total);
		} catch (IOException exception) {
			throw new SearchInfrastructureUnavailableException("Elasticsearch search is unavailable", exception);
		}
	}

	public static int validatedOffset(int page, int size) {
		if (page < 0) throw new IllegalArgumentException("The 'page' parameter must be at least 0.");
		if (size < 1 || size > 100) throw new IllegalArgumentException("The 'size' parameter must be between 1 and 100.");
		long offset = (long) page * size;
		if (offset + size > MAX_RESULT_WINDOW) {
			throw new IllegalArgumentException("The requested page exceeds the searchable result window of " + MAX_RESULT_WINDOW + ".");
		}
		return Math.toIntExact(offset);
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

	@Schema(requiredProperties = {"entryId", "date", "highlights"})
	public record EntrySearchResult(
			String entryId,
			LocalDate date,
			@Schema(description = "Plain-text fragments keyed only by mood.note, habits.note, or mood.tags; ranges use zero-based, end-exclusive offsets.")
			Map<String, List<HighlightFragment>> highlights
	) {
	}
	@Schema(requiredProperties = {"text", "ranges"})
	public record HighlightFragment(String text, List<HighlightRange> ranges) {}
	@Schema(requiredProperties = {"start", "end"})
	public record HighlightRange(int start, int end) {}
}
