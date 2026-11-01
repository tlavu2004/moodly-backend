package com.tlavu.moodly.modules.search.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tlavu.moodly.modules.search.service.view.EntrySearchView;
import com.tlavu.moodly.modules.search.service.view.HighlightFragmentView;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EntrySearchResponseTest {
	@Test
	void keepsHighlightFieldOrderAndPreventsMutation() {
		Map<String, List<HighlightFragmentView>> highlights = new LinkedHashMap<>();
		highlights.put("mood.note", List.of(new HighlightFragmentView("note", List.of())));
		highlights.put("mood.tags", List.of(new HighlightFragmentView("tag", List.of())));
		EntrySearchView view = new EntrySearchView("entry-1", LocalDate.of(2026, 8, 6), highlights);

		EntrySearchResponse response = EntrySearchResponse.from(view);

		assertThat(response.highlights().keySet()).containsExactly("mood.note", "mood.tags");
		assertThatThrownBy(() -> response.highlights().clear())
				.isInstanceOf(UnsupportedOperationException.class);
	}
}
