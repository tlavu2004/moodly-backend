package com.tlavu.moodly.modules.cdc.infrastructure;

import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.tlavu.moodly.modules.cdc.application.CdcDeliveryService;
import com.tlavu.moodly.modules.cdc.application.CdcMonitor;
import com.tlavu.moodly.modules.cdc.application.DailyEntryReindexService;
import com.tlavu.moodly.modules.cdc.domain.CdcResumeToken;
import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import java.time.Instant;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonDocument;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.messaging.ChangeStreamRequest;
import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
import org.springframework.data.mongodb.core.messaging.Message;
import org.springframework.data.mongodb.core.messaging.Subscription;
import org.springframework.stereotype.Component;

/** Maintains Elasticsearch as an asynchronous, rebuildable projection of daily_entries. */
@Slf4j
@Component
@ConditionalOnProperty(name = "moodly.cdc.enabled", havingValue = "true", matchIfMissing = true)
public class DailyEntryChangeStreamListener {

	private static final String STREAM_ID = "daily_entries";

	private final DefaultMessageListenerContainer listenerContainer;
	private final CdcResumeTokenRepository resumeTokenRepository;
	private final CdcDeliveryService deliveryService;
	private final DailyEntryReindexService reindexService;
	private final CdcMonitor monitor;
	private Subscription subscription;

	public DailyEntryChangeStreamListener(
			MongoTemplate mongoTemplate,
			CdcResumeTokenRepository resumeTokenRepository,
			CdcDeliveryService deliveryService,
			DailyEntryReindexService reindexService,
			CdcMonitor monitor
	) {
		this.listenerContainer = new DefaultMessageListenerContainer(mongoTemplate);
		this.resumeTokenRepository = resumeTokenRepository;
		this.deliveryService = deliveryService;
		this.reindexService = reindexService;
		this.monitor = monitor;
	}

	@EventListener(ApplicationReadyEvent.class)
	public synchronized void start() {
		listenerContainer.start();
		register(resumeTokenRepository.findById(STREAM_ID).map(CdcResumeToken::getToken).orElse(null));
		monitor.listenerStarted();
	}

	private void register(String resumeToken) {
		var builder = ChangeStreamRequest.builder(this::onMessage)
				.collection("daily_entries")
				.fullDocumentLookup(FullDocument.UPDATE_LOOKUP);
		if (resumeToken != null) {
			builder.resumeAfter(BsonDocument.parse(resumeToken));
		}
		subscription = listenerContainer.register(builder.build(), DailyEntry.class, this::onListenerFailure);
		log.info("Started daily_entries Change Stream{}.", resumeToken == null ? "" : " from its resume token");
	}

	private void onMessage(Message<ChangeStreamDocument<org.bson.Document>, DailyEntry> message) {
		try {
			var raw = Objects.requireNonNull(message.getRaw(), "Change Stream message has no raw event");
			var resumeToken = Objects.requireNonNull(raw.getResumeToken(), "Change Stream event has no resume token");
			var eventId = resumeToken.toString();
			if (raw.getOperationType() == com.mongodb.client.model.changestream.OperationType.DELETE) {
				var documentKey = Objects.requireNonNull(raw.getDocumentKey(), "Delete event has no document key");
				var entryId = Objects.requireNonNull(documentKey.getObjectId("_id"), "Delete event has no entry ID")
						.getValue().toHexString();
				deliveryService.deliverDelete(eventId, entryId);
			} else if (raw.getOperationType() == com.mongodb.client.model.changestream.OperationType.INSERT
					|| raw.getOperationType() == com.mongodb.client.model.changestream.OperationType.UPDATE
					|| raw.getOperationType() == com.mongodb.client.model.changestream.OperationType.REPLACE) {
				deliveryService.deliverUpsert(eventId, raw.getOperationType().getValue(), message.getBody());
			} else {
				return;
			}
			resumeTokenRepository.save(new CdcResumeToken(STREAM_ID, resumeToken.toString(), Instant.now()));
		} catch (Exception exception) {
			throw new IllegalStateException("Could not synchronize a daily_entries change event", exception);
		}
	}

	private synchronized void onListenerFailure(@NonNull Throwable exception) {
		monitor.listenerFailed(exception);
		if (!isExpiredResumeToken(exception)) {
			log.error("daily_entries Change Stream stopped unexpectedly.", exception);
			return;
		}

		log.warn("The daily_entries Change Stream resume token has expired; rebuilding the Elasticsearch index.", exception);
		try {
			listenerContainer.remove(subscription);
			resumeTokenRepository.deleteById(STREAM_ID);
			reindexService.reindex();
			register(null);
			monitor.listenerStarted();
		} catch (Exception recoveryFailure) {
			log.error("Could not recover daily_entries Change Stream with a full reindex.", recoveryFailure);
		}
	}

	private boolean isExpiredResumeToken(Throwable exception) {
		var message = exception.getMessage();
		return message != null && (message.contains("ChangeStreamHistoryLost") || message.contains("code 286"));
	}
}
