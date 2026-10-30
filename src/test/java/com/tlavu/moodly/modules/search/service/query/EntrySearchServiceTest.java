package com.tlavu.moodly.modules.search.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.search.service.input.EntrySearchInput;
import com.tlavu.moodly.modules.search.service.view.EntrySearchPageView;
import com.tlavu.moodly.shared.error.exception.common.InvalidRequestException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntrySearchServiceTest {

	@Mock
	private EntrySearchQueryPort queryPort;

	@Test
	void passesTheValidatedOffsetAndReturnsTheSearchPage() {
		EntrySearchInput input = new EntrySearchInput("tired", null, null, 99, 100);
		EntrySearchPageView expected = new EntrySearchPageView(List.of(), 99, 100, 10_000);
		when(queryPort.search("user-1", input, 9_900)).thenReturn(expected);

		EntrySearchPageView actual = new EntrySearchService(queryPort).search("user-1", input);

		assertThat(actual).isSameAs(expected);
		verify(queryPort).search("user-1", input, 9_900);
	}

	@Test
	void rejectsAnUnsupportedResultWindowBeforeQueryingElasticsearch() {
		EntrySearchInput input = new EntrySearchInput("tired", null, null, Integer.MAX_VALUE, 100);

		assertThatThrownBy(() -> new EntrySearchService(queryPort).search("user-1", input))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("result window");
		verifyNoInteractions(queryPort);
	}
}
