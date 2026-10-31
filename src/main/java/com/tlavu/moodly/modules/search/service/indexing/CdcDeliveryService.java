package com.tlavu.moodly.modules.search.service.indexing;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.tlavu.moodly.modules.search.monitoring.CdcMonitor;
import com.tlavu.moodly.modules.search.model.mongodb.CdcDeadLetter;
import com.tlavu.moodly.modules.search.model.elasticsearch.DailyEntrySearchDocument;
import com.tlavu.moodly.modules.search.repository.CdcDeadLetterRepository;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.DailyEntrySearchWriter;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchFailureClassifier;
import com.tlavu.moodly.modules.entries.model.DailyEntry;
import com.tlavu.moodly.shared.error.exception.common.SearchInfrastructureUnavailableException;
import com.tlavu.moodly.shared.error.exception.common.ResourceNotFoundException;
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
	private static final long MAX_BACKOFF_MS = 30_000;

	private final DailyEntrySearchWriter searchWriter;
	private final CdcDeadLetterRepository deadLetterRepository;
	private final CdcMonitor monitor;
	private final ObjectMapper objectMapper;
	private final CdcRetrySleeper retrySleeper;
	private final int maxAttempts;
	private final Duration initialBackoff;

	public CdcDeliveryService(
			DailyEntrySearchWriter searchWriter,
			CdcDeadLetterRepository deadLetterRepository,
			CdcMonitor monitor,
			ObjectMapper objectMapper,
			CdcRetrySleeper retrySleeper,
			@Value("${moodly.cdc.retry.max-attempts}") int maxAttempts,
			@Value("${moodly.cdc.retry.initial-backoff-ms}") long initialBackoffMs
	) {
		this.searchWriter = searchWriter;
		this.deadLetterRepository = deadLetterRepository;
		this.monitor = monitor;
		this.objectMapper = objectMapper;
		this.retrySleeper = retrySleeper;
		this.maxAttempts = maxAttempts;
		this.initialBackoff = Duration.ofMillis(initialBackoffMs);
	}

	public void deliverUpsert(
			String eventId,
			String operationType,
			DailyEntry entry
	) {
		DailyEntrySearchDocument document = DailyEntrySearchDocument.from(entry);

		deliver(
				eventId,
				operationType,
				entry.getId(),
				serialize(document),
				() -> searchWriter.index(entry.getId(), document)
		);
	}

	public void deliverDelete(String eventId, String entryId) {
		deliver(
			eventId,
			"delete",
			entryId,
			null,
			() -> searchWriter.delete(entryId)
		);
	}

	public void replay(String deadLetterId) {
		CdcDeadLetter deadLetter = deadLetterRepository.findById(deadLetterId)
				.orElseThrow(() -> new ResourceNotFoundException("CDC dead letter was not found."));
		replayDeadLetter(deadLetter);
	}

	private void replayDeadLetter(CdcDeadLetter deadLetter) {
		AttemptCount attempts = new AttemptCount();
		try {
			if ("delete".equals(deadLetter.getOperationType())) {
				deliverWithRetry(
					deadLetter.getEventId(),
					"delete",
					deadLetter.getEntryId(),
					() -> searchWriter.delete(deadLetter.getEntryId()),
					attempts
				);
			} else {
				DailyEntrySearchDocument document = objectMapper.readValue(
						deadLetter.getPayload(),
						DailyEntrySearchDocument.class
				);

				deliverWithRetry(
						deadLetter.getEventId(),
						deadLetter.getOperationType(),
						deadLetter.getEntryId(),
						() -> searchWriter.index(deadLetter.getEntryId(), document),
						attempts
				);
			}
			deadLetterRepository.delete(deadLetter);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			handleReplayFailure(deadLetter, exception, attempts.value());

			throw new IllegalStateException(
					"CDC dead-letter replay was interrupted " + deadLetter.getId(),
					exception
			);
		} catch (IOException exception) {
			handleReplayFailure(deadLetter, exception, attempts.value());

			throw SearchInfrastructureUnavailableException.duringReplay(exception);
		} catch (ElasticsearchException exception) {
			handleReplayFailure(deadLetter, exception, attempts.value());

			if (ElasticsearchFailureClassifier.isUnavailable(exception)) {
				throw SearchInfrastructureUnavailableException.duringReplay(exception);
			}

			throw new IllegalStateException(
					"Could not replay CDC dead-letter event " + deadLetter.getId(),
					exception
			);
		} catch (RuntimeException exception) {
			handleReplayFailure(deadLetter, exception, attempts.value());

			throw new IllegalStateException(
					"Could not replay CDC dead-letter event " + deadLetter.getId(),
					exception
			);
		}
	}

	private void handleReplayFailure(CdcDeadLetter deadLetter, Exception exception, int attemptsMade) {
		deadLetter.setAttempts(deadLetter.getAttempts() + attemptsMade);
		deadLetter.setError(exception.getMessage());
		deadLetter.setFailedAt(Instant.now());
		deadLetterRepository.save(deadLetter);
		monitor.deadLettered(exception);
	}

	private void deliver(String eventId, String operationType, String entryId, String payload, ThrowingOperation operation) {
		AttemptCount attempts = new AttemptCount();
		try {
			deliverWithRetry(
					eventId,
					operationType,
					entryId,
					operation,
					attempts
			);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();

			saveDeadLetter(
					eventId,
					operationType,
					entryId,
					payload,
					exception,
					attempts.value()
			);
		} catch (IOException | RuntimeException exception) {
			saveDeadLetter(
					eventId,
					operationType,
					entryId,
					payload,
					exception,
					attempts.value()
			);
		}
	}

	private void saveDeadLetter(
			String eventId,
			String operationType,
			String entryId,
			String payload,
			Exception exception,
			int attemptsMade
	) {
		deadLetterRepository.save(
				new CdcDeadLetter(
						eventId,
						operationType,
						entryId,
						payload,
						exception.getMessage(),
						attemptsMade,
						Instant.now()
				)
		);
		monitor.deadLettered(exception);

		log.error(
				"CDC event moved to dead letter: eventId={}, operation={}, entryId={}, attempts={}",
				eventId,
				operationType,
				entryId,
				attemptsMade,
				exception
		);
	}

	private void deliverWithRetry(
			String eventId,
			String operationType,
			String entryId,
			ThrowingOperation operation,
			AttemptCount attempts
	) throws IOException, InterruptedException {
		IOException lastIoFailure = null;
		RuntimeException lastRuntimeFailure = null;

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			attempts.record(attempt);
			try {
				operation.run();

				return;
			} catch (IOException exception) {
				if (isNonTransient(exception)) {
					throw exception;
				}

				lastIoFailure = exception;

				if (attempt == maxAttempts) {
					break;
				}

				waitBeforeRetry(
						eventId,
						operationType,
						entryId,
						attempt,
						exception
				);
			} catch (RuntimeException exception) {
				if (isNonTransient(exception)) {
					throw exception;
				}

				lastRuntimeFailure = exception;

				if (attempt == maxAttempts) {
					break;
				}

				waitBeforeRetry(
						eventId,
						operationType,
						entryId,
						attempt,
						exception
				);
			}
		}

		if (lastIoFailure != null) {
			throw lastIoFailure;
		}
		if (lastRuntimeFailure != null) {
			throw lastRuntimeFailure;
		}

		throw new IOException("CDC delivery failed without an Elasticsearch exception");
	}

	private void waitBeforeRetry(
			String eventId,
			String operationType,
			String entryId,
			int attempt,
			Exception exception
	) throws InterruptedException {
		long delayMs = Math.min(initialBackoff.toMillis(), MAX_BACKOFF_MS);
		for (int retry = 1; retry < attempt && delayMs > 0 && delayMs < MAX_BACKOFF_MS; retry++) {
			delayMs = Math.min(delayMs * 2, MAX_BACKOFF_MS);
		}
		Duration delay = Duration.ofMillis(delayMs);

		log.warn(
				"Retrying CDC Elasticsearch delivery: eventId={}, operation={}, entryId={}, " +
						"attempt={}, maxAttempts={}, delayMs={}",
				eventId,
				operationType,
				entryId,
				attempt,
				maxAttempts,
				delay.toMillis(),
				exception
		);

		retrySleeper.sleep(delay);
	}

	private boolean isNonTransient(Exception exception) {
		if (exception instanceof IOException) {
			return false;
		}

		return !(exception instanceof ElasticsearchException elasticsearchException) ||
				(elasticsearchException.status() != 408 &&
						elasticsearchException.status() != 429 &&
						elasticsearchException.status() < 500);
	}

	private String serialize(DailyEntrySearchDocument document) {
		try {
			return objectMapper.writeValueAsString(document);
		} catch (JacksonException exception) {
			throw new IllegalStateException(
					"Could not serialize CDC search document",
					exception
			);
		}
	}

	@FunctionalInterface
	private interface ThrowingOperation {
		void run() throws IOException;
	}

	private static final class AttemptCount {
		private int value;

		void record(int attempt) {
			value = attempt;
		}

		int value() {
			return value;
		}
	}
}
