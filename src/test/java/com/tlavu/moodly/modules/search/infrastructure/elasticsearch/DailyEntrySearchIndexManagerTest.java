package com.tlavu.moodly.modules.search.infrastructure.elasticsearch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import java.io.IOException;

@ExtendWith(MockitoExtension.class)
class DailyEntrySearchIndexManagerTest {

	@Mock
	private ElasticsearchClient elasticsearchClient;

	@Test
	void doesNotCreateAnExistingIndex() throws Exception {
		when(elasticsearchClient.exists("entries-test")).thenReturn(true);
		DailyEntrySearchIndexManager manager = manager();

		manager.createIfMissing();

		verify(elasticsearchClient, never()).createIndex(any(), any());
	}

	@Test
	void createsMissingIndexFromConfiguredMapping() throws Exception {
		when(elasticsearchClient.exists("entries-test")).thenReturn(false);
		DailyEntrySearchIndexManager manager = manager();

		manager.createIfMissing();

			verify(elasticsearchClient).createIndex("entries-test", "{\"mappings\":{}}");
	}

	@Test
	void recreatesExistingIndex() throws Exception {
		when(elasticsearchClient.exists("entries-test")).thenReturn(true);
		DailyEntrySearchIndexManager manager = manager();

		manager.recreate();

			InOrder order = org.mockito.Mockito.inOrder(elasticsearchClient);
		order.verify(elasticsearchClient).deleteIndex("entries-test");
		order.verify(elasticsearchClient).createIndex("entries-test", "{\"mappings\":{}}");
	}

	@Test
	void doesNotDeleteAnExistingIndexWhenItsMappingCannotBeRead() throws Exception {
		Resource unreadableMapping = org.mockito.Mockito.mock(Resource.class);
		when(unreadableMapping.getInputStream()).thenThrow(new IOException("mapping unavailable"));
		DailyEntrySearchIndexManager manager = new DailyEntrySearchIndexManager(
				elasticsearchClient, "entries-test", unreadableMapping);

		assertThatThrownBy(manager::recreate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Could not read the configured Elasticsearch index mapping")
				.hasCauseInstanceOf(IOException.class);
		verifyNoInteractions(elasticsearchClient);
	}

	@Test
	void treatsUnreadableMappingAsInternalWhenCreatingAMissingIndex() throws Exception {
		when(elasticsearchClient.exists("entries-test")).thenReturn(false);
		Resource unreadableMapping = org.mockito.Mockito.mock(Resource.class);
		when(unreadableMapping.getInputStream()).thenThrow(new IOException("mapping unavailable"));
		DailyEntrySearchIndexManager manager = new DailyEntrySearchIndexManager(
				elasticsearchClient, "entries-test", unreadableMapping);

		assertThatThrownBy(manager::createIfMissing)
				.isInstanceOf(IllegalStateException.class)
				.hasCauseInstanceOf(IOException.class);
		verify(elasticsearchClient, never()).createIndex(any(), any());
	}

	@Test
	void preservesElasticsearchIoFailureForTheCallerToClassify() throws Exception {
		ElasticsearchTransportException transportFailure = new ElasticsearchTransportException("Elasticsearch unavailable");
		when(elasticsearchClient.exists("entries-test")).thenThrow(transportFailure);

		assertThatThrownBy(manager()::createIfMissing).isSameAs(transportFailure);
	}

	private DailyEntrySearchIndexManager manager() {
		return new DailyEntrySearchIndexManager(
				elasticsearchClient,
				"entries-test",
				new ByteArrayResource("{\"mappings\":{}}".getBytes())
		);
	}

}
