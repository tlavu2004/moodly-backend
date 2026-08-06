package com.tlavu.moodly.modules.cdc.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import com.tlavu.moodly.modules.cdc.application.CdcDeliveryService;
import com.tlavu.moodly.modules.cdc.application.CdcMonitor;
import com.tlavu.moodly.modules.cdc.application.DailyEntryReindexService;
import com.tlavu.moodly.modules.cdc.domain.CdcResumeToken;
import com.tlavu.moodly.modules.entries.domain.DailyEntry;
import java.lang.reflect.Method;
import java.util.Optional;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
import org.springframework.data.mongodb.core.messaging.Message;
import org.springframework.data.mongodb.core.messaging.Subscription;

@ExtendWith(MockitoExtension.class)
class DailyEntryChangeStreamListenerTest {

	@Mock
	private DefaultMessageListenerContainer listenerContainer;
	@Mock
	private CdcResumeTokenRepository resumeTokenRepository;
	@Mock
	private CdcDeliveryService deliveryService;
	@Mock
	private DailyEntryReindexService reindexService;
	@Mock
	private CdcMonitor monitor;
	@Mock
	private Subscription subscription;
	@Mock
	private Message<ChangeStreamDocument<org.bson.Document>, DailyEntry> message;
	@Mock
	private ChangeStreamDocument<org.bson.Document> raw;

	private DailyEntryChangeStreamListener listener;

	@BeforeEach
	void setUp() {
		listener = new DailyEntryChangeStreamListener(
				listenerContainer, resumeTokenRepository, deliveryService, reindexService, monitor, "entries-test", "stream-test");
	}

	@Test
	void routesInsertAndPersistsResumeTokenAfterSuccessfulDelivery() throws Exception {
		var entry = new DailyEntry("user-1", java.time.LocalDate.of(2026, 8, 6));
		var token = BsonDocument.parse("{_data: 'token'}");
		when(message.getRaw()).thenReturn(raw);
		when(message.getBody()).thenReturn(entry);
		when(raw.getResumeToken()).thenReturn(token);
		when(raw.getOperationType()).thenReturn(OperationType.INSERT);

		invoke("onMessage", Message.class, message);

		verify(deliveryService).deliverUpsert(token.toString(), "insert", entry);
		var savedToken = ArgumentCaptor.forClass(CdcResumeToken.class);
		verify(resumeTokenRepository).save(savedToken.capture());
		org.assertj.core.api.Assertions.assertThat(savedToken.getValue().getId()).isEqualTo("stream-test");
	}

	@Test
	void routesUpdateAndReplaceToUpsert() throws Exception {
		var entry = new DailyEntry("user-1", java.time.LocalDate.of(2026, 8, 6));
		var token = BsonDocument.parse("{_data: 'token'}");
		when(message.getRaw()).thenReturn(raw);
		when(message.getBody()).thenReturn(entry);
		when(raw.getResumeToken()).thenReturn(token);
		when(raw.getOperationType()).thenReturn(OperationType.UPDATE, OperationType.REPLACE);

		invoke("onMessage", Message.class, message);
		invoke("onMessage", Message.class, message);

		verify(deliveryService).deliverUpsert(token.toString(), "update", entry);
		verify(deliveryService).deliverUpsert(token.toString(), "replace", entry);
	}

	@Test
	void routesDeleteAndLeavesUnsupportedOperationWithoutAdvancingToken() throws Exception {
		var token = BsonDocument.parse("{_data: 'token'}");
		when(message.getRaw()).thenReturn(raw);
		when(raw.getResumeToken()).thenReturn(token);
		when(raw.getOperationType()).thenReturn(OperationType.DELETE);
		when(raw.getDocumentKey()).thenReturn(new BsonDocument("_id", new BsonObjectId(new ObjectId("64b7abdecf2160b649ab6085"))));

		invoke("onMessage", Message.class, message);

		verify(deliveryService).deliverDelete(token.toString(), "64b7abdecf2160b649ab6085");
		verify(resumeTokenRepository).save(any(CdcResumeToken.class));
	}

	@Test
	void ignoresUnsupportedOperationsWithoutAdvancingTheResumeToken() throws Exception {
		when(message.getRaw()).thenReturn(raw);
		when(raw.getResumeToken()).thenReturn(BsonDocument.parse("{_data: 'token'}"));
		when(raw.getOperationType()).thenReturn(OperationType.INVALIDATE);

		invoke("onMessage", Message.class, message);

		verifyNoInteractions(deliveryService, resumeTokenRepository);
	}

	@Test
	void rebuildsAfterExpiredResumeTokenButNotAfterOtherFailures() throws Exception {
		when(resumeTokenRepository.findById("stream-test")).thenReturn(Optional.empty());
		when(listenerContainer.register(any(), eq(DailyEntry.class), any())).thenReturn(subscription);
		listener.start();

		invoke("onListenerFailure", Throwable.class, new IllegalStateException("code 286"));

		verify(listenerContainer).remove(subscription);
		verify(resumeTokenRepository).deleteById("stream-test");
		verify(reindexService).reindex();
		verify(monitor, org.mockito.Mockito.times(2)).listenerStarted();

		invoke("onListenerFailure", Throwable.class, new IllegalStateException("network error"));
		verify(reindexService).reindex();
		verify(monitor, org.mockito.Mockito.times(2)).listenerFailed(any(Throwable.class));
	}

	private void invoke(String name, Class<?> parameterType, Object argument) throws Exception {
		Method method = DailyEntryChangeStreamListener.class.getDeclaredMethod(name, parameterType);
		method.setAccessible(true);
		method.invoke(listener, argument);
	}
}
