package com.tlavu.moodly.modules.cdc.application;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.tlavu.moodly.modules.cdc.domain.CdcDeadLetter;
import com.tlavu.moodly.modules.cdc.infrastructure.CdcDeadLetterRepository;
import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class CdcDeliveryService {

	private final DailyEntrySearchWriter searchWriter;
	private final CdcDeadLetterRepository deadLetterRepository;
	private final CdcMonitor monitor;
	private final ObjectMapper objectMapper;
	private final int maxAttempts;
	private final Duration initialBackoff;

	public CdcDeliveryService(
			DailyEntrySearchWriter searchWriter,
			CdcDeadLetterRepository deadLetterRepository,
			CdcMonitor monitor,
			ObjectMapper objectMapper,
			@Value("${moodly.cdc.retry.max-attempts}") int maxAttempts,
			@Value("${moodly.cdc.retry.initial-backoff-ms}") long initialBackoffMs
	) {
		this.searchWriter = searchWriter;
		this.deadLetterRepository = deadLetterRepository;
		this.monitor = monitor;
		this.objectMapper = objectMapper;
		this.maxAttempts = maxAttempts;
		this.initialBackoff = Duration.ofMillis(initialBackoffMs);
	}

	public void deliverUpsert(String eventId, String operationType, DailyEntry entry) {
		var document = DailyEntrySearchDocument.from(entry);
		deliver(eventId, operationType, entry.getId(), serialize(document), () -> searchWriter.index(entry.getId(), document));
	}

	public void deliverDelete(String eventId, String entryId) {
		deliver(eventId, "delete", entryId, null, () -> searchWriter.delete(entryId));
	}

	public void replay(CdcDeadLetter deadLetter) {
		try {
			if ("delete".equals(deadLetter.getOperationType())) {
				deliverWithRetry(deadLetter.getEventId(), "delete", deadLetter.getEntryId(), () -> searchWriter.delete(deadLetter.getEntryId()));
			} else {
				var document = objectMapper.readValue(deadLetter.getPayload(), DailyEntrySearchDocument.class);
				deliverWithRetry(deadLetter.getEventId(), deadLetter.getOperationType(), deadLetter.getEntryId(),
						() -> searchWriter.index(deadLetter.getEntryId(), document));
			}
			deadLetterRepository.delete(deadLetter);
			monitor.replayedSuccessfully();
		} catch (Exception exception) {
			deadLetter.setAttempts(deadLetter.getAttempts() + maxAttempts);
			deadLetter.setError(exception.getMessage());
			deadLetter.setFailedAt(Instant.now());
			deadLetterRepository.save(deadLetter);
			monitor.deadLettered(exception);
			throw new IllegalStateException("Could not replay CDC dead-letter event " + deadLetter.getId(), exception);
		}
	}

	private void deliver(String eventId, String operationType, String entryId, String payload, ThrowingOperation operation) {
		try {
			deliverWithRetry(eventId, operationType, entryId, operation);
		} catch (Exception exception) {
			deadLetterRepository.save(new CdcDeadLetter(
					eventId,
					operationType,
					entryId,
					payload,
					exception.getMessage(),
					maxAttempts,
					Instant.now()
			));
			monitor.deadLettered(exception);
			log.error("CDC event moved to dead letter: eventId={}, operation={}, entryId={}, attempts={}",
					eventId, operationType, entryId, maxAttempts, exception);
		}
	}

	private void deliverWithRetry(String eventId, String operationType, String entryId, ThrowingOperation operation) throws Exception {
		Exception lastFailure = null;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				operation.run();
				return;
			} catch (Exception exception) {
				if (!isTransient(exception)) {
					throw exception;
				}
				lastFailure = exception;
				if (attempt == maxAttempts) {
					break;
				}
				var delay = initialBackoff.multipliedBy(1L << (attempt - 1));
				log.warn("Retrying CDC Elasticsearch delivery: eventId={}, operation={}, entryId={}, attempt={}, maxAttempts={}, delayMs={}",
						eventId, operationType, entryId, attempt, maxAttempts, delay.toMillis(), exception);
				Thread.sleep(delay);
			}
		}
		throw lastFailure == null ? new IOException("CDC delivery failed without an Elasticsearch exception") : lastFailure;
	}

	private boolean isTransient(Exception exception) {
		if (exception instanceof IOException) {
			return true;
		}
		return exception instanceof ElasticsearchException elasticsearchException
				&& (elasticsearchException.status() == 429 || elasticsearchException.status() >= 500);
	}

	private String serialize(DailyEntrySearchDocument document) {
		try {
			return objectMapper.writeValueAsString(document);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Could not serialize CDC search document", exception);
		}
	}

	@FunctionalInterface
	private interface ThrowingOperation {
		void run() throws IOException;
	}
}
