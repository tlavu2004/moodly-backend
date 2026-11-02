package com.tlavu.moodly.modules.search.infrastructure.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import java.io.IOException;
import java.util.function.Function;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"}) // Elasticsearch client request lambdas are captured by erased runtime type.
class DailyEntrySearchIndexManagerTest {

	@Mock
	private ElasticsearchClient elasticsearchClient;
	@Mock
	private ElasticsearchIndicesClient indicesClient;

	@Test
	void doesNotCreateAnExistingIndex() throws Exception {
		when(elasticsearchClient.indices()).thenReturn(indicesClient);
		when(indicesClient.exists(any(Function.class))).thenReturn(new BooleanResponse(true));
		DailyEntrySearchIndexManager manager = manager();

		manager.createIfMissing();

		verify(indicesClient, never()).create(any(Function.class));
	}

	@Test
	void createsMissingIndexFromConfiguredMapping() throws Exception {
		when(elasticsearchClient.indices()).thenReturn(indicesClient);
		when(indicesClient.exists(any(Function.class))).thenReturn(new BooleanResponse(false));
		DailyEntrySearchIndexManager manager = manager();

		manager.createIfMissing();

		@SuppressWarnings("rawtypes")
		ArgumentCaptor<Function> request = ArgumentCaptor.forClass((Class) Function.class);
		verify(indicesClient).create(request.capture());
		assertThat(buildCreateRequest(request.getValue()).index()).isEqualTo("entries-test");
	}

	@Test
	void recreatesExistingIndex() throws Exception {
		when(elasticsearchClient.indices()).thenReturn(indicesClient);
		when(indicesClient.exists(any(Function.class))).thenReturn(new BooleanResponse(true));
		DailyEntrySearchIndexManager manager = manager();

		manager.recreate();

		@SuppressWarnings("rawtypes")
		ArgumentCaptor<Function> deleteRequest = ArgumentCaptor.forClass((Class) Function.class);
		verify(indicesClient).delete(deleteRequest.capture());
		assertThat(buildDeleteRequest(deleteRequest.getValue()).index()).containsExactly("entries-test");
		verify(indicesClient).create(any(Function.class));
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
		verifyNoInteractions(elasticsearchClient, indicesClient);
	}

	@Test
	void treatsUnreadableMappingAsInternalWhenCreatingAMissingIndex() throws Exception {
		when(elasticsearchClient.indices()).thenReturn(indicesClient);
		when(indicesClient.exists(any(Function.class))).thenReturn(new BooleanResponse(false));
		Resource unreadableMapping = org.mockito.Mockito.mock(Resource.class);
		when(unreadableMapping.getInputStream()).thenThrow(new IOException("mapping unavailable"));
		DailyEntrySearchIndexManager manager = new DailyEntrySearchIndexManager(
				elasticsearchClient, "entries-test", unreadableMapping);

		assertThatThrownBy(manager::createIfMissing)
				.isInstanceOf(IllegalStateException.class)
				.hasCauseInstanceOf(IOException.class);
		verify(indicesClient, never()).create(any(Function.class));
	}

	@Test
	void preservesElasticsearchIoFailureForTheCallerToClassify() throws Exception {
		when(elasticsearchClient.indices()).thenReturn(indicesClient);
		IOException transportFailure = new IOException("Elasticsearch unavailable");
		when(indicesClient.exists(any(Function.class))).thenThrow(transportFailure);

		assertThatThrownBy(manager()::createIfMissing).isSameAs(transportFailure);
	}

	private DailyEntrySearchIndexManager manager() {
		return new DailyEntrySearchIndexManager(
				elasticsearchClient,
				"entries-test",
				new ByteArrayResource("{\"mappings\":{}}".getBytes())
		);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private CreateIndexRequest buildCreateRequest(Function requestBuilder) {
		return ((co.elastic.clients.util.ObjectBuilder<CreateIndexRequest>) requestBuilder.apply(new CreateIndexRequest.Builder())).build();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private DeleteIndexRequest buildDeleteRequest(Function requestBuilder) {
		return ((co.elastic.clients.util.ObjectBuilder<DeleteIndexRequest>) requestBuilder.apply(new DeleteIndexRequest.Builder())).build();
	}
}
