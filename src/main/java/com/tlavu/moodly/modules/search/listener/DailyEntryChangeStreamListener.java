package com.tlavu.moodly.modules.search.listener;

import com.tlavu.moodly.shared.error.logging.ExceptionLogSummary;

import com.tlavu.moodly.modules.search.repository.CdcResumeTokenRepository;

import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.MongoException;
import com.tlavu.moodly.modules.search.service.indexing.CdcDeliveryService;
import com.tlavu.moodly.modules.search.monitoring.CdcMonitor;
import com.tlavu.moodly.modules.search.service.indexing.DailyEntryReindexService;
import com.tlavu.moodly.modules.search.model.mongodb.CdcResumeToken;
import com.tlavu.moodly.modules.entries.model.DailyEntry;
import java.time.Instant;
import com.tlavu.moodly.shared.error.exception.common.OperationInterruptedException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.BsonTimestamp;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.messaging.ChangeStreamRequest;
import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
import org.springframework.data.mongodb.core.messaging.Message;
import org.springframework.data.mongodb.core.messaging.Subscription;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Maintains Elasticsearch as an asynchronous, rebuildable projection of daily_entries. */
@Slf4j
@Component
@ConditionalOnProperty(name = "moodly.cdc.enabled", havingValue = "true", matchIfMissing = true)
public class DailyEntryChangeStreamListener {
	private final DefaultMessageListenerContainer listenerContainer;
	private final CdcResumeTokenRepository resumeTokenRepository;
	private final CdcDeliveryService deliveryService;
	private final DailyEntryReindexService reindexService;
	private final CdcMonitor monitor;
	private final String collectionName;
	private final String streamId;
	private final Supplier<Instant> operationTime;
	private Subscription subscription;
	private boolean recoveryPending;
	private boolean rebuildPending;

	@Autowired
	public DailyEntryChangeStreamListener(
			MongoTemplate mongoTemplate,
			CdcResumeTokenRepository resumeTokenRepository,
			CdcDeliveryService deliveryService,
			DailyEntryReindexService reindexService,
			CdcMonitor monitor,
			@Value("${moodly.entries.collection-name}") String collectionName,
			@Value("${moodly.cdc.stream-id}") String streamId
	) {
		this(
				new DefaultMessageListenerContainer(mongoTemplate),
				resumeTokenRepository,
				deliveryService,
				reindexService,
				monitor,
				collectionName,
				streamId,
				() -> mongoOperationTime(mongoTemplate)
		);
	}

	DailyEntryChangeStreamListener(
			DefaultMessageListenerContainer listenerContainer,
			CdcResumeTokenRepository resumeTokenRepository,
			CdcDeliveryService deliveryService,
			DailyEntryReindexService reindexService,
			CdcMonitor monitor,
			String collectionName,
			String streamId,
			Supplier<Instant> operationTime
	) {
		this.listenerContainer = listenerContainer;
		this.resumeTokenRepository = resumeTokenRepository;
		this.deliveryService = deliveryService;
		this.reindexService = reindexService;
		this.monitor = monitor;
		this.collectionName = collectionName;
		this.streamId = streamId;
		this.operationTime = operationTime;
	}

	@EventListener(ApplicationReadyEvent.class)
	public synchronized void start() {
		recoveryPending = false;
		rebuildPending = false;
		CdcResumeToken checkpoint = resumeTokenRepository.findById(streamId).orElse(null);
		if (checkpoint == null || reindexService.isIndexMissing()) {
			Instant baseline = Objects.requireNonNull(operationTime.get(), "MongoDB did not return an operation time");
			reindexService.reindex();
			checkpoint = CdcResumeToken.fromOperationTime(streamId, baseline, Instant.now());
			resumeTokenRepository.save(checkpoint);
		}
		listenerContainer.start();
		register(checkpoint);
		monitor.listenerStarted();
	}

	/** Stops the stream without deleting its persisted resume token. */
	public synchronized void stop() {
		recoveryPending = false;
		rebuildPending = false;
		if (subscription != null) {
			listenerContainer.remove(subscription);
			subscription = null;
		}
		listenerContainer.stop();
	}

	/** Retries transient MongoDB failures and incomplete history-lost rebuilds. */
	@Scheduled(fixedDelayString = "${moodly.cdc.recovery-delay-ms:30000}")
	public synchronized void recoverTransientFailure() {
		if (rebuildPending) {
			rebuildAfterHistoryLost();
			return;
		}
		if (!recoveryPending) {
			return;
		}

		recoveryPending = false;
		try {
			stop();
			start();
		} catch (RuntimeException exception) {
			recoveryPending = true;
			monitor.listenerFailed(exception);
			log.error("Could not restart {} Change Stream from its resume token: {}", collectionName, ExceptionLogSummary.of(exception));
		}
	}

	/** Waits until the registered change-stream subscription is active. */
	public synchronized boolean awaitRegistration(Duration timeout) {
		try {
			return subscription != null && subscription.await(timeout);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new OperationInterruptedException("CDC subscription registration interrupted", interrupted);
		}
	}

	private void register(CdcResumeToken checkpoint) {
		ChangeStreamRequest.ChangeStreamRequestBuilder<DailyEntry> builder =
				ChangeStreamRequest.builder(this::onMessage)
						.collection(collectionName)
						.fullDocumentLookup(FullDocument.UPDATE_LOOKUP);

		if (checkpoint != null && checkpoint.getToken() != null) {
			builder.resumeAfter(BsonDocument.parse(checkpoint.getToken()));
		} else if (checkpoint != null && checkpoint.getResumeAt() != null) {
			builder.resumeAt(checkpoint.getResumeAt());
		}

		subscription = listenerContainer.register(
				builder.build(),
				DailyEntry.class,
				this::onListenerFailure
		);

		log.info(
				"Started {} Change Stream{}.",
				collectionName,
				checkpoint == null
						? ""
						: " from its persisted checkpoint"
		);
	}

	private void onMessage(
			Message<ChangeStreamDocument<Document>,
			DailyEntry> message
	) {
		try {
			ChangeStreamDocument<Document> raw = Objects.requireNonNull(
					message.getRaw(),
					"Change Stream message has no raw event"
			);
			BsonDocument resumeToken = Objects.requireNonNull(
				raw.getResumeToken(),
				"Change Stream event has no resume token"
			);
			String eventId = resumeToken.toString();
			OperationType operationType = raw.getOperationType();

			if (operationType == OperationType.DELETE) {
				BsonDocument documentKey = Objects.requireNonNull(
						raw.getDocumentKey(),
						"Delete event has no document key"
				);
				BsonValue id = Objects.requireNonNull(
						documentKey.get("_id"),
						"Delete event has no entry ID"
				);
				String entryId = switch (id.getBsonType()) {
					case OBJECT_ID -> id.asObjectId().getValue().toHexString();
					case STRING -> id.asString().getValue();
					default -> throw new IllegalStateException(
							"Delete event has an unsupported entry ID type: " + id.getBsonType()
					);
				};

				deliveryService.deliverDelete(eventId, entryId);
			} else if (
					operationType == OperationType.INSERT ||
					operationType == OperationType.UPDATE ||
					operationType == OperationType.REPLACE
			) {
				deliveryService.deliverUpsert(
						eventId,
						operationType.getValue(),
						message.getBody()
				);
			} else {
				return;
			}

			resumeTokenRepository.save(
					new CdcResumeToken(
							streamId,
							resumeToken.toString(),
							Instant.now()
					)
			);
		} catch (Exception exception) {
			throw new IllegalStateException(
					"Could not synchronize a " + collectionName + " change event",
					exception
			);
		}
	}

	private synchronized void onListenerFailure(@NonNull Throwable exception) {
		monitor.listenerFailed(exception);

		if (!isExpiredResumeToken(exception)) {
			recoveryPending = isTransientMongoFailure(exception);
			log.error(
					"{} Change Stream stopped unexpectedly (automaticRecovery={}): {}",
					collectionName,
					recoveryPending,
					ExceptionLogSummary.of(exception)
			);

			return;
		}

		log.warn(
				"The {} Change Stream resume token has expired; rebuilding the Elasticsearch index: {}",
				collectionName,
				ExceptionLogSummary.of(exception)
		);

		rebuildPending = true;
		rebuildAfterHistoryLost();
	}

	private void rebuildAfterHistoryLost() {
		try {
			if (subscription != null) {
				listenerContainer.remove(subscription);
				subscription = null;
			}
			Instant baseline = Objects.requireNonNull(operationTime.get(), "MongoDB did not return an operation time");
			reindexService.reindex();
			CdcResumeToken checkpoint = CdcResumeToken.fromOperationTime(streamId, baseline, Instant.now());
			resumeTokenRepository.save(checkpoint);
			register(checkpoint);
			rebuildPending = false;
			monitor.listenerStarted();
		} catch (Exception recoveryFailure) {
			monitor.listenerFailed(recoveryFailure);
			log.error(
					"Could not recover {} Change Stream with a full reindex: {}",
					collectionName,
					ExceptionLogSummary.of(recoveryFailure)
			);
		}
	}

	private boolean isTransientMongoFailure(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof MongoSocketException || cause instanceof MongoTimeoutException ||
					cause instanceof TransientDataAccessException || cause instanceof DataAccessResourceFailureException) {
				return true;
			}
		}
		return false;
	}

	private boolean isExpiredResumeToken(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof MongoException mongoException && mongoException.getCode() == 286) {
				return true;
			}
		}
		return false;
	}

	private static Instant mongoOperationTime(MongoTemplate mongoTemplate) {
		Document response = mongoTemplate.executeCommand(new Document("ping", 1));
		BsonTimestamp timestamp = response.get("operationTime", BsonTimestamp.class);
		return timestamp == null ? null : Instant.ofEpochSecond(timestamp.getTime());
	}
}
