package com.tlavu.moodly.modules.cdc.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.cdc.domain.CdcDeadLetter;
import com.tlavu.moodly.modules.cdc.infrastructure.CdcDeadLetterRepository;
import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CdcDeliveryServiceTest {

	@Mock
	private DailyEntrySearchWriter searchWriter;
	@Mock
	private CdcDeadLetterRepository deadLetterRepository;
	@Mock
	private CdcMonitor monitor;
	@Mock
	private ObjectMapper objectMapper;
	@Mock
	private CdcRetrySleeper retrySleeper;

	@Test
	void deliversDeleteWithoutCreatingADeadLetter() throws Exception {
		var service = service();

		service.deliverDelete("event-1", "entry-1");

		verify(searchWriter).delete("entry-1");
		verifyNoInteractions(deadLetterRepository, retrySleeper);
	}

	@Test
	void deliversUpsertWithoutCreatingADeadLetter() throws Exception {
		var entry = new DailyEntry("user-1", java.time.LocalDate.of(2026, 8, 6));
		entry.setId("entry-1");
		when(objectMapper.writeValueAsString(any(DailyEntrySearchDocument.class))).thenReturn("serialized-payload");
		var service = service();

		service.deliverUpsert("event-1", "insert", entry);

		verify(searchWriter).index(eq("entry-1"), any(DailyEntrySearchDocument.class));
		verifyNoInteractions(deadLetterRepository, retrySleeper);
	}

	@Test
	void retriesTransientDeliveryAndSucceeds() throws Exception {
		doThrow(new IOException("Elasticsearch unavailable"))
				.doThrow(new IOException("Elasticsearch unavailable"))
				.doNothing()
				.when(searchWriter).delete("entry-1");
		var service = service();

		service.deliverDelete("event-1", "entry-1");

		verify(searchWriter, times(3)).delete("entry-1");
		verify(retrySleeper).sleep(Duration.ofMillis(1));
		verify(retrySleeper).sleep(Duration.ofMillis(2));
		verifyNoInteractions(deadLetterRepository);
	}

	@Test
	void preservesTheInterruptFlagAndStoresDeadLetterWhenRetrySleepIsInterrupted() throws Exception {
		doThrow(new IOException("Elasticsearch unavailable")).when(searchWriter).delete("entry-1");
		doThrow(new InterruptedException("shutdown")).when(retrySleeper).sleep(Duration.ofMillis(1));
		var service = service();

		try {
			service.deliverDelete("event-1", "entry-1");

			verify(deadLetterRepository).save(any(CdcDeadLetter.class));
		} finally {
			assertThat(Thread.interrupted()).isTrue();
		}
	}

	@Test
	void storesDeadLetterAfterTransientRetriesAreExhausted() throws Exception {
		doThrow(new IOException("Elasticsearch unavailable")).when(searchWriter).delete("entry-1");
		var service = service();

		service.deliverDelete("event-1", "entry-1");

		var deadLetter = ArgumentCaptor.forClass(CdcDeadLetter.class);
		verify(deadLetterRepository).save(deadLetter.capture());
		assertThat(deadLetter.getValue().getEventId()).isEqualTo("event-1");
		assertThat(deadLetter.getValue().getOperationType()).isEqualTo("delete");
		assertThat(deadLetter.getValue().getEntryId()).isEqualTo("entry-1");
		assertThat(deadLetter.getValue().getAttempts()).isEqualTo(3);
		verify(searchWriter, times(3)).delete("entry-1");
		verify(monitor).deadLettered(any(IOException.class));
	}

	@Test
	void storesSerializedUpsertPayloadInTheDeadLetter() throws Exception {
		var entry = new DailyEntry("user-1", java.time.LocalDate.of(2026, 8, 6));
		entry.setId("entry-1");
		when(objectMapper.writeValueAsString(any(DailyEntrySearchDocument.class))).thenReturn("serialized-payload");
		doThrow(new IOException("Elasticsearch unavailable"))
				.when(searchWriter).index(eq("entry-1"), any(DailyEntrySearchDocument.class));
		var service = service();

		service.deliverUpsert("event-1", "update", entry);

		var deadLetter = ArgumentCaptor.forClass(CdcDeadLetter.class);
		verify(deadLetterRepository).save(deadLetter.capture());
		assertThat(deadLetter.getValue().getPayload()).isEqualTo("serialized-payload");
		assertThat(deadLetter.getValue().getFailedAt()).isNotNull();
	}

	@Test
	void doesNotRetryNonTransientFailures() throws Exception {
		var badRequest = org.mockito.Mockito.mock(ElasticsearchException.class);
		when(badRequest.status()).thenReturn(400);
		doThrow(badRequest).when(searchWriter).delete("entry-1");
		var service = service();

		service.deliverDelete("event-1", "entry-1");

		verify(searchWriter).delete("entry-1");
		verifyNoInteractions(retrySleeper);
	}

	@Test
	void retriesRateLimitedElasticsearchFailures() throws Exception {
		var rateLimited = org.mockito.Mockito.mock(ElasticsearchException.class);
		when(rateLimited.status()).thenReturn(429);
		doThrow(rateLimited).doNothing().when(searchWriter).delete("entry-1");
		var service = service();

		service.deliverDelete("event-1", "entry-1");

		verify(searchWriter, times(2)).delete("entry-1");
		verify(retrySleeper).sleep(Duration.ofMillis(1));
	}

	@Test
	void retriesServerSideElasticsearchFailures() throws Exception {
		var serverFailure = org.mockito.Mockito.mock(ElasticsearchException.class);
		when(serverFailure.status()).thenReturn(503);
		doThrow(serverFailure).doNothing().when(searchWriter).delete("entry-1");
		var service = service();

		service.deliverDelete("event-1", "entry-1");

		verify(searchWriter, times(2)).delete("entry-1");
		verify(retrySleeper).sleep(Duration.ofMillis(1));
	}

	@Test
	void deletesDeadLetterAfterSuccessfulReplay() throws Exception {
		var deadLetter = new CdcDeadLetter("event-1", "delete", "entry-1", null, "old", 3, Instant.now());
		var service = service();

		service.replay(deadLetter);

		verify(searchWriter).delete("entry-1");
		verify(deadLetterRepository).delete(deadLetter);
		verify(monitor).replayedSuccessfully();
	}

	@Test
	void retainsDeadLetterAndAddsAttemptsWhenReplayFails() throws Exception {
		doThrow(new IOException("Elasticsearch unavailable")).when(searchWriter).delete("entry-1");
		var deadLetter = new CdcDeadLetter("event-1", "delete", "entry-1", null, "old", 3, Instant.now());
		var service = service();

		assertThatThrownBy(() -> service.replay(deadLetter))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Could not replay CDC dead-letter event");

		assertThat(deadLetter.getAttempts()).isEqualTo(6);
		assertThat(deadLetter.getError()).isEqualTo("Elasticsearch unavailable");
		verify(deadLetterRepository).save(deadLetter);
		verify(monitor).deadLettered(any(IOException.class));
	}

	private CdcDeliveryService service() {
		return new CdcDeliveryService(searchWriter, deadLetterRepository, monitor, objectMapper, retrySleeper, 3, 1);
	}
}
