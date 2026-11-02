package com.tlavu.moodly.modules.search.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tlavu.moodly.modules.search.exception.SearchException;
import org.junit.jupiter.api.Test;

class EntrySearchPaginationTest {
	@Test
	void acceptsTheLastPageWithinTheResultWindow() {
		assertThat(EntrySearchPagination.validatedOffset(0, 20)).isZero();
		assertThat(EntrySearchPagination.validatedOffset(99, 100)).isEqualTo(9_900);
		assertThat(EntrySearchPagination.validatedOffset(9_999, 1)).isEqualTo(9_999);
	}

	@Test
	void rejectsPagesOutsideTheResultWindowWithoutIntegerOverflow() {
		assertThatThrownBy(() -> EntrySearchPagination.validatedOffset(100, 100))
				.isInstanceOf(SearchException.class)
				.hasMessageContaining("result window");
		assertThatThrownBy(() -> EntrySearchPagination.validatedOffset(Integer.MAX_VALUE, 100))
				.isInstanceOf(SearchException.class)
				.hasMessageContaining("result window");
	}

	@Test
	void rejectsInvalidPageAndSize() {
		assertThatThrownBy(() -> EntrySearchPagination.validatedOffset(-1, 20))
				.isInstanceOf(SearchException.class)
				.hasMessageContaining("page");
		assertThatThrownBy(() -> EntrySearchPagination.validatedOffset(0, 0))
				.isInstanceOf(SearchException.class)
				.hasMessageContaining("size");
		assertThatThrownBy(() -> EntrySearchPagination.validatedOffset(0, 101))
				.isInstanceOf(SearchException.class)
				.hasMessageContaining("size");
	}
}
