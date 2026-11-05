package com.tlavu.moodly.modules.search.infrastructure.elasticsearch;

import com.tlavu.moodly.modules.search.exception.SearchDocumentMalformedException;

import tools.jackson.databind.JsonNode;
import com.tlavu.moodly.modules.search.service.port.EntrySearchQueryPort;
import com.tlavu.moodly.modules.search.service.input.EntrySearchInput;
import com.tlavu.moodly.modules.search.service.view.EntrySearchPageView;
import com.tlavu.moodly.modules.search.service.view.EntrySearchView;
import com.tlavu.moodly.modules.search.service.view.HighlightFragmentView;
import com.tlavu.moodly.modules.search.service.view.HighlightRangeView;
import com.tlavu.moodly.shared.error.exception.common.SearchInfrastructureUnavailableException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchEntrySearchAdapter implements EntrySearchQueryPort {
	private static final String HIGHLIGHT_START_TAG = "<em>";
	private static final String HIGHLIGHT_END_TAG = "</em>";
	private static final List<String> SEARCH_FIELDS = List.of(
			"mood.note",
			"habits.note",
			"mood.tags"
	);
	private final ElasticsearchClient elasticsearchClient;
	private final DailyEntrySearchIndexManager indexManager;

	@Override
	public EntrySearchPageView search(
			String userId,
			EntrySearchInput input,
			int offset
	) {
		String query = input.query();
		LocalDate from = input.from();
		LocalDate to = input.to();
		int page = input.page();
		int size = input.size();

		try {
			List<Object> filters = new ArrayList<>();
			filters.add(
					Map.of("term", Map.of("userId", userId))
			);

			Map<String, String> dates = new LinkedHashMap<>();

			if (from != null) {
				dates.put("gte", from.toString());
			}
			if (to != null) {
				dates.put("lte", to.toString());
			}
			if (!dates.isEmpty()) {
				filters.add(Map.of("range", Map.of("date", dates)));
			}

			Map<String, Object> highlight = new LinkedHashMap<>();
			SEARCH_FIELDS.forEach(field -> highlight.put(field, Map.of()));

			JsonNode hits = elasticsearchClient.search(
					indexManager.getIndexName(),
					Map.of(
							"from", offset,
							"size", size,
							"track_total_hits", true,
							"sort", List.of(
									Map.of("_score", "desc"),
									Map.of("date", "desc")
							),
							"query", Map.of(
									"bool", Map.of(
											"must", Map.of(
													"multi_match", Map.of(
															"query", query,
															"fields", SEARCH_FIELDS
													)
											),
											"filter", filters
									)
							),
							"highlight", Map.of(
									"fields", highlight,
									"pre_tags", List.of(HIGHLIGHT_START_TAG),
									"post_tags", List.of(HIGHLIGHT_END_TAG)
							)
					)
			).path("hits");

			List<EntrySearchView> items = new ArrayList<>();

			for (JsonNode hit : hits.path("hits")) {
				String id = hit.path("_id").asString();
				JsonNode source = hit.path("_source");

				if (!source.isObject()) {
					log.error(
							"Search index inconsistency: hitId={} is missing _source; rebuild the index",
							id
					);

					throw new SearchDocumentMalformedException(
							"Elasticsearch hit " + id + " has no source"
					);
				}

				Map<String, List<String>> fragments = new LinkedHashMap<>();

				hit.path("highlight").properties()
						.forEach(field -> {
							List<String> values = new ArrayList<>();

							field.getValue().forEach(value -> values.add(value.asString()));
							fragments.put(field.getKey(), values);
						});
				items.add(
						new EntrySearchView(
								id,
								LocalDate.parse(source.path("date").asString()),
								normalizeHighlights(fragments)
						)
				);
			}

			long total = hits.path("total")
					.path("value")
					.asLong(items.size());

			return new EntrySearchPageView(
					List.copyOf(items),
					page,
					size,
					total
			);
		} catch (ElasticsearchTransportException exception) {
			throw SearchInfrastructureUnavailableException.duringSearch(exception);
		} catch (ElasticsearchException exception) {
			if (ElasticsearchFailureClassifier.isUnavailable(exception)) {
				throw SearchInfrastructureUnavailableException.duringSearch(exception);
			}

			throw exception;
		}
	}

	private Map<String, List<HighlightFragmentView>> normalizeHighlights(
			Map<String, List<String>> raw
	) {
		if (raw == null || raw.isEmpty()) {
			return Map.of();
		}

		Map<String, List<HighlightFragmentView>> normalized = new LinkedHashMap<>();

		raw.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.forEach(entry -> normalized.put(
						entry.getKey(),
						entry.getValue().stream()
								.map(this::plainFragment)
								.toList()
				));

		return Collections.unmodifiableMap(normalized);
	}

	private HighlightFragmentView plainFragment(String marked) {
		StringBuilder text = new StringBuilder();
		List<HighlightRangeView> ranges = new ArrayList<>();
		int cursor = 0;

		while (cursor < marked.length()) {
			int startTag = marked.indexOf(HIGHLIGHT_START_TAG, cursor);

			if (startTag < 0) {
				text.append(marked, cursor, marked.length());
				break;
			}

			text.append(marked, cursor, startTag);

			int endTag = marked.indexOf(
					HIGHLIGHT_END_TAG,
					startTag + HIGHLIGHT_START_TAG.length()
			);

			if (endTag < 0) {
				text.append(marked, startTag, marked.length());
				break;
			}

			int start = text.length();

			text.append(marked, startTag + HIGHLIGHT_START_TAG.length(), endTag);
			ranges.add(new HighlightRangeView(start, text.length()));
			cursor = endTag + HIGHLIGHT_END_TAG.length();
		}

		return new HighlightFragmentView(text.toString(), List.copyOf(ranges));
	}
}
