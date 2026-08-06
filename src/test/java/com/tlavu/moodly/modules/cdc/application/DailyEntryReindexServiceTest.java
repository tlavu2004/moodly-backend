package com.tlavu.moodly.modules.cdc.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import com.tlavu.moodly.modules.entries.infrastructure.DailyEntryRepository;
import com.tlavu.moodly.modules.search.infrastructure.DailyEntrySearchIndexManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DailyEntryReindexServiceTest {

	@Mock
	private DailyEntryRepository dailyEntryRepository;
	@Mock
	private DailyEntrySearchWriter searchWriter;
	@Mock
	private DailyEntrySearchIndexManager indexManager;

	@Test
	void recreatesIndexAndIndexesEveryPageUsingConfiguredBatchSize() throws Exception {
		var first = new DailyEntry("user-1", LocalDate.of(2026, 8, 5));
		var second = new DailyEntry("user-1", LocalDate.of(2026, 8, 6));
		var third = new DailyEntry("user-1", LocalDate.of(2026, 8, 7));
		var firstPageRequest = PageRequest.of(0, 2);
		var secondPageRequest = PageRequest.of(1, 2);
		when(dailyEntryRepository.findAll(firstPageRequest))
				.thenReturn(new PageImpl<>(List.of(first, second), firstPageRequest, 3));
		when(dailyEntryRepository.findAll(secondPageRequest))
				.thenReturn(new PageImpl<>(List.of(third), secondPageRequest, 3));

		var service = new DailyEntryReindexService(dailyEntryRepository, searchWriter, indexManager, 2);

		assertThat(service.reindex().indexedEntries()).isEqualTo(3);
		var order = inOrder(indexManager, searchWriter);
		order.verify(indexManager).recreate();
		order.verify(searchWriter).index(first);
		order.verify(searchWriter).index(second);
		order.verify(searchWriter).index(third);
		verify(dailyEntryRepository).findAll(firstPageRequest);
		verify(dailyEntryRepository).findAll(secondPageRequest);
	}

	@Test
	void returnsZeroWhenTheSourceCollectionIsEmpty() throws Exception {
		var pageRequest = PageRequest.of(0, 2);
		when(dailyEntryRepository.findAll(pageRequest)).thenReturn(new PageImpl<>(List.of(), pageRequest, 0));
		var service = new DailyEntryReindexService(dailyEntryRepository, searchWriter, indexManager, 2);

		assertThat(service.reindex().indexedEntries()).isZero();
		verify(indexManager).recreate();
	}

	@Test
	void indexesACompleteSinglePage() throws Exception {
		var entry = new DailyEntry("user-1", LocalDate.of(2026, 8, 6));
		var pageRequest = PageRequest.of(0, 2);
		when(dailyEntryRepository.findAll(pageRequest)).thenReturn(new PageImpl<>(List.of(entry), pageRequest, 1));
		var service = new DailyEntryReindexService(dailyEntryRepository, searchWriter, indexManager, 2);

		assertThat(service.reindex().indexedEntries()).isEqualTo(1);
		verify(searchWriter).index(entry);
	}
}
