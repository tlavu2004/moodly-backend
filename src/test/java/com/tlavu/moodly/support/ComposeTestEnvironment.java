package com.tlavu.moodly.support;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.tlavu.moodly.modules.search.listener.DailyEntryChangeStreamListener;
import org.springframework.context.ConfigurableApplicationContext;

/** Reserved test namespaces on shared local infrastructure; never owns its lifecycle. */
// Keep lambdas for JDT method-reference nullability compatibility.
@SuppressWarnings("Convert2MethodRef")
final class ComposeTestEnvironment {
	private static ComposeTestEnvironment instance;
	private final Map<String, String> settings = new LinkedHashMap<>();
	private final List<ConfigurableApplicationContext> contexts = new ArrayList<>();
	private SharedInfrastructure.TestLease lease;
	private boolean closed;
	private boolean initialized;

	static synchronized void finishCurrentGroup() {
		if (instance != null) {
			instance.finishGroup();
		}
	}

	static synchronized void stop() {
		if (instance != null) {
			instance.close();
			instance = null;
		}
	}

	static synchronized ComposeTestEnvironment get() {
		if (instance == null) {
			instance = new ComposeTestEnvironment();
		}
		return instance;
	}

	private ComposeTestEnvironment() {
		lease = SharedInfrastructure.lease(SharedInfrastructure.LOCK_FILE);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				close();
			} catch (Exception failure) {
				System.err.println("Compose shutdown cleanup failed: " + failure.getClass().getSimpleName());
			}
		}, "moodly-compose-test-cleanup"));
		try {
			var configuration = SharedInfrastructure.configuration();
			settings.putAll(configuration.testSettings());
			database();
			index();
			SharedInfrastructure.start(configuration);
			initialized = true;
			System.out.println("Integration tests use reserved test namespaces on shared stack moodly-local.");
		} catch (RuntimeException failure) {
			close();
			throw failure;
		}
	}

	synchronized void prepare(ConfigurableApplicationContext context) {
		closeContexts();
		resetData();
		contexts.add(context);
	}

	synchronized void finishGroup() {
		closeContexts();
		resetData();
	}

	String database() {
		String database = settings.get("MONGODB_DATABASE");
		if (!"moodly_test".equals(database)) {
			throw new IllegalStateException("Only the reserved database moodly_test may be reset");
		}
		return database;
	}

	String index() {
		String index = settings.get("MOODLY_SEARCH_INDEX_NAME");
		if (!"daily_entries_search_test".equals(index)) {
			throw new IllegalStateException("Only the reserved index daily_entries_search_test may be reset");
		}
		return index;
	}

	private void closeContexts() {
		for (ConfigurableApplicationContext context : contexts) {
			if (context.isActive()) {
				context.getBeansOfType(DailyEntryChangeStreamListener.class).values()
						.forEach(listener -> listener.stop());
			}
			context.close();
		}
		contexts.clear();
	}

	private void resetData() {
		try (MongoClient mongo = MongoClients.create(mongoUri(database())); HttpClient http = HttpClient.newHttpClient()) {
			mongo.getDatabase(database()).drop();
			HttpResponse<Void> response = http.send(HttpRequest.newBuilder(URI.create(searchUri() + "/" + index()))
					.timeout(Duration.ofSeconds(10)).DELETE().build(), HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() != 200 && response.statusCode() != 404) {
				throw new IllegalStateException("Could not reset test index: HTTP " + response.statusCode());
			}
			System.out.println("Reset Compose test database " + database() + " and index " + index());
		} catch (IOException failure) {
			throw new IllegalStateException("Could not reset Compose test data", failure);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted resetting Compose test data", failure);
		}
	}

	String mongoUri(String database) {
		return "mongodb://" + settings.get("MONGODB_HOST") + ":" + settings.get("MONGODB_PORT")
				+ "/" + database + "?replicaSet=" + settings.get("MONGODB_REPLICA_SET");
	}

	String searchUri() {
		return "http://" + settings.get("ELASTICSEARCH_HOST") + ":" + settings.get("ELASTICSEARCH_PORT");
	}

	private synchronized void close() {
		if (closed) {
			return;
		}
		closed = true;
		boolean failed = false;
		try {
			closeContexts();
			if (initialized) { resetData(); }
		} catch (Exception failure) {
			failed = true;
			System.err.println("Could not reset Compose test data: " + failure.getClass().getSimpleName());
		} finally {
			if (lease != null) { lease.close(); }
		}
		if (failed) {
			throw new IllegalStateException("Test cleanup failed; shared infrastructure was left running.");
		}
	}

}
