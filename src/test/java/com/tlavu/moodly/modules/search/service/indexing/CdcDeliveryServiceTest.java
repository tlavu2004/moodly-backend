package com.tlavu.moodly.modules.search.service.indexing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.search.model.mongodb.CdcDeadLetter;
import com.tlavu.moodly.modules.search.monitoring.CdcMonitor;
import com.tlavu.moodly.modules.search.model.elasticsearch.DailyEntrySearchDocument;
import com.tlavu.moodly.modules.search.repository.CdcDeadLetterRepository;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchWriter;
import com.tlavu.moodly.modules.entries.model.DailyEntry;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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
		CdcDeliveryService service = service();

		service.deliverDelete("event-1", "entry-1");

		verify(searchWriter).delete("entry-1");
		verifyNoInteractions(deadLetterRepository, retrySleeper);
	}

	@Test
	void deliversUpsertWithoutCreatingADeadLetter() throws Exception {
		DailyEntry entry = new DailyEntry("user-1", java.time.LocalDate.of(2026, 8, 6));
		entry.setId("entry-1");
		when(objectMapper.writeValueAsString(any(DailyEntrySearchDocument.class))).thenReturn("serialized-payload");
		CdcDeliveryService service = service();

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
		CdcDeliveryService service = service();

		service.deliverDelete("event-1", "entry-1");

		verify(searchWriter, times(3)).delete("entry-1");
		verify(retrySleeper).sleep(Duration.ofMillis(1));
		verify(retrySleeper).sleep(Duration.ofMillis(2));
		verifyNoInteractions(deadLetterRepository);
	}

	@Test
	void capsBackoffWhenAttemptExceedsLongShiftRange() throws Exception {
		doThrow(new IOException("Elasticsearch unavailable")).when(searchWriter).delete("entry-1");
		CdcDeliveryService service = new CdcDeliveryService(
				searchWriter, deadLetterRepository, monitor, objectMapper, retrySleeper, 66, 1
		);

		service.deliverDelete("event-1", "entry-1");

		ArgumentCaptor<Duration> delays = ArgumentCaptor.forClass(Duration.class);
		verify(retrySleeper, times(65)).sleep(delays.capture());
		assertThat(delays.getAllValues()).first().isEqualTo(Duration.ofMillis(1));
		assertThat(delays.getAllValues()).allSatisfy(delay ->
				assertThat(delay).isBetween(Duration.ofMillis(1), Duration.ofSeconds(30)));
		assertThat(delays.getAllValues()).last().isEqualTo(Duration.ofSeconds(30));
	}

	@Test
	void preservesTheInterruptFlagAndStoresDeadLetterWhenRetrySleepIsInterrupted() throws Exception {
		doThrow(new IOException("Elasticsearch unavailable")).when(searchWriter).delete("entry-1");
		doThrow(new InterruptedException("shutdown")).when(retrySleeper).sleep(Duration.ofMillis(1));
		CdcDeliveryService service = service();

		try {
			service.deliverDelete("event-1", "entry-1");

			ArgumentCaptor<CdcDeadLetter> deadLetter = ArgumentCaptor.forClass(CdcDeadLetter.class);
			verify(deadLetterRepository).save(deadLetter.capture());
			assertThat(deadLetter.getValue().getAttempts()).isEqualTo(1);
		} finally {
			assertThat(Thread.interrupted()).isTrue();
		}
	}

	@Test
	void storesDeadLetterAfterTransientRetriesAreExhausted() throws Exception {
		doThrow(new IOException("Elasticsearch unavailable")).when(searchWriter).delete("entry-1");
		CdcDeliveryService service = service();

		service.deliverDelete("event-1", "entry-1");

		ArgumentCaptor<CdcDeadLetter> deadLetter = ArgumentCaptor.forClass(CdcDeadLetter.class);
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
		DailyEntry entry = new DailyEntry("user-1", java.time.LocalDate.of(2026, 8, 6));
		entry.setId("entry-1");
		when(objectMapper.writeValueAsString(any(DailyEntrySearchDocument.class))).thenReturn("serialized-payload");
		doThrow(new IOException("Elasticsearch unavailable"))
				.when(searchWriter).index(eq("entry-1"), any(DailyEntrySearchDocument.class));
		CdcDeliveryService service = service();

		service.deliverUpsert("event-1", "update", entry);

		ArgumentCaptor<CdcDeadLetter> deadLetter = ArgumentCaptor.forClass(CdcDeadLetter.class);
		verify(deadLetterRepository).save(deadLetter.capture());
		assertThat(deadLetter.getValue().getPayload()).isEqualTo("serialized-payload");
		assertThat(deadLetter.getValue().getFailedAt()).isNotNull();
	}

	@Test
	void doesNotRetryNonTransientFailures() throws Exception {
		ElasticsearchException badRequest = org.mockito.Mockito.mock(ElasticsearchException.class);
		when(badRequest.status()).thenReturn(400);
		doThrow(badRequest).when(searchWriter).delete("entry-1");
		CdcDeliveryService service = service();

		service.deliverDelete("event-1", "entry-1");

		ArgumentCaptor<CdcDeadLetter> deadLetter = ArgumentCaptor.forClass(CdcDeadLetter.class);
		verify(deadLetterRepository).save(deadLetter.capture());
		assertThat(deadLetter.getValue().getAttempts()).isEqualTo(1);
		verify(searchWriter).delete("entry-1");
		verifyNoInteractions(retrySleeper);
	}

	@Test
	void retriesRateLimitedElasticsearchFailures() throws Exception {
		ElasticsearchException rateLimited = org.mockito.Mockito.mock(ElasticsearchException.class);
		when(rateLimited.status()).thenReturn(429);
		doThrow(rateLimited).doNothing().when(searchWriter).delete("entry-1");
		CdcDeliveryService service = service();

		service.deliverDelete("event-1", "entry-1");

		verify(searchWriter, times(2)).delete("entry-1");
		verify(retrySleeper).sleep(Duration.ofMillis(1));
	}

	@Test
	void retriesElasticsearchRequestTimeout() throws Exception {
		ElasticsearchException timeout = org.mockito.Mockito.mock(ElasticsearchException.class);
		when(timeout.status()).thenReturn(408);
		doThrow(timeout).doNothing().when(searchWriter).delete("entry-1");
		CdcDeliveryService service = service();

		service.deliverDelete("event-1", "entry-1");

		verify(searchWriter, times(2)).delete("entry-1");
		verify(retrySleeper).sleep(Duration.ofMillis(1));
		verifyNoInteractions(deadLetterRepository);
	}

	@Test
	void retriesServerSideElasticsearchFailures() throws Exception {
		ElasticsearchException serverFailure = org.mockito.Mockito.mock(ElasticsearchException.class);
		when(serverFailure.status()).thenReturn(503);
		doThrow(serverFailure).doNothing().when(searchWriter).delete("entry-1");
		CdcDeliveryService service = service();

		service.deliverDelete("event-1", "entry-1");

		verify(searchWriter, times(2)).delete("entry-1");
		verify(retrySleeper).sleep(Duration.ofMillis(1));
	}

	@Test
	void deletesDeadLetterAfterSuccessfulReplay() throws Exception {
		CdcDeadLetter deadLetter = new CdcDeadLetter("event-1", "delete", "entry-1", null, "old", 3, Instant.now());
		when(deadLetterRepository.findById("dead-letter-1")).thenReturn(Optional.of(deadLetter));
		CdcDeliveryService service = service();

		service.replay("dead-letter-1");

		verify(searchWriter).delete("entry-1");
		verify(deadLetterRepository).delete(deadLetter);
		verify(monitor).replayedSuccessfully();
	}

	@Test
	void retainsDeadLetterAndAddsAttemptsWhenReplayFails() throws Exception {
		doThrow(new IOException("Elasticsearch unavailable")).when(searchWriter).delete("entry-1");
		CdcDeadLetter deadLetter = new CdcDeadLetter("event-1", "delete", "entry-1", null, "old", 3, Instant.now());
		when(deadLetterRepository.findById("dead-letter-1")).thenReturn(Optional.of(deadLetter));
		CdcDeliveryService service = service();

		assertThatThrownBy(() -> service.replay("dead-letter-1"))
				.isInstanceOf(com.tlavu.moodly.shared.error.exception.common.SearchInfrastructureUnavailableException.class)
				.hasMessageContaining("CDC dead-letter replay is unavailable");

		assertThat(deadLetter.getAttempts()).isEqualTo(6);
		assertThat(deadLetter.getError()).isEqualTo("Elasticsearch unavailable");
		verify(deadLetterRepository).save(deadLetter);
		verify(monitor).deadLettered(any(IOException.class));
	}

	@Test
	void reportsElasticsearchBadRequestAsAnInternalReplayFailure() throws Exception {
		ElasticsearchException badRequest = org.mockito.Mockito.mock(ElasticsearchException.class);
		when(badRequest.status()).thenReturn(400);
		doThrow(badRequest).when(searchWriter).delete("entry-1");
		CdcDeadLetter deadLetter = new CdcDeadLetter("event-1", "delete", "entry-1", null, "old", 3, Instant.now());
		when(deadLetterRepository.findById("dead-letter-1")).thenReturn(java.util.Optional.of(deadLetter));
		CdcDeliveryService service = service();

		assertThatThrownBy(() -> service.replay("dead-letter-1"))
				.isInstanceOf(IllegalStateException.class)
				.hasCause(badRequest);
		assertThat(deadLetter.getAttempts()).isEqualTo(4);
		verify(deadLetterRepository).save(deadLetter);
	}

	@Test
	void doesNotCountAReplayAttemptWhenPayloadCannotBeRead() throws Exception {
		CdcDeadLetter deadLetter = new CdcDeadLetter("event-1", "update", "entry-1", "invalid", "old", 3, Instant.now());
		when(deadLetterRepository.findById("dead-letter-1")).thenReturn(Optional.of(deadLetter));
		when(objectMapper.readValue("invalid", DailyEntrySearchDocument.class))
				.thenThrow(new IllegalStateException("invalid payload"));
		CdcDeliveryService service = service();

		assertThatThrownBy(() -> service.replay("dead-letter-1"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Could not replay");

		assertThat(deadLetter.getAttempts()).isEqualTo(3);
		verifyNoInteractions(searchWriter);
		verify(deadLetterRepository).save(deadLetter);
	}

	@Test
	void reportsMissingDeadLetterBeforeWritingToElasticsearch() {
		when(deadLetterRepository.findById("missing")).thenReturn(Optional.empty());
		CdcDeliveryService service = service();

		assertThatThrownBy(() -> service.replay("missing"))
				.isInstanceOf(com.tlavu.moodly.shared.error.exception.common.ResourceNotFoundException.class)
				.hasMessage("CDC dead letter was not found.");
		verifyNoInteractions(searchWriter);
	}

	private CdcDeliveryService service() {
		return new CdcDeliveryService(searchWriter, deadLetterRepository, monitor, objectMapper, retrySleeper, 3, 1);
	}
}
