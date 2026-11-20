package com.tlavu.moodly.modules.search.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchIndexManager;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchException;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchTransportException;
import com.tlavu.moodly.shared.error.exception.common.SearchInfrastructureUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

class DailyEntrySearchIndexInitializerTest {
	private final DailyEntrySearchIndexManager indexManager = mock(DailyEntrySearchIndexManager.class);
	private final DailyEntrySearchIndexInitializer initializer = new DailyEntrySearchIndexInitializer(indexManager);
	private final DefaultApplicationArguments arguments = new DefaultApplicationArguments();

	@Test
	void leavesIndexInitializationToCdcWhenEnabled() {
		ReflectionTestUtils.setField(initializer, "cdcEnabled", true);

		initializer.run(arguments);

		verifyNoInteractions(indexManager);
	}

	@Test
	void createsIndexIfMissingWhenCdcIsDisabled() {
		initializer.run(arguments);

		verify(indexManager).createIfMissing();
	}

	@ParameterizedTest
	@MethodSource("unavailableFailures")
	void wrapsElasticsearchUnavailableFailuresAndPreservesTheCause(RuntimeException failure) {
		doThrow(failure).when(indexManager).createIfMissing();

		assertThatThrownBy(() -> initializer.run(arguments))
				.isInstanceOf(SearchInfrastructureUnavailableException.class)
				.hasMessage("Elasticsearch index initialization is unavailable")
				.hasCause(failure);
	}

	@Test
	void propagatesForbiddenWithoutReclassifyingIt() {
		ElasticsearchException failure = new ElasticsearchException(403);
		doThrow(failure).when(indexManager).createIfMissing();

		assertThatThrownBy(() -> initializer.run(arguments)).isSameAs(failure);
	}

	private static RuntimeException[] unavailableFailures() {
		return new RuntimeException[]{
				new ElasticsearchTransportException("Connection unavailable"),
				new ElasticsearchException(503)
		};
	}
}
