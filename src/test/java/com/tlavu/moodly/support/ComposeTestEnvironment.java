package com.tlavu.moodly.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.tlavu.moodly.modules.search.listener.DailyEntryChangeStreamListener;
import org.springframework.context.ConfigurableApplicationContext;

/** Reserved test namespaces; participates in shared infrastructure session ownership. */
// Keep lambdas for JDT method-reference nullability compatibility.
@SuppressWarnings("Convert2MethodRef")
final class ComposeTestEnvironment {
	private static ComposeTestEnvironment instance;
	private final Map<String, String> settings = new LinkedHashMap<>();
	private final List<ConfigurableApplicationContext> contexts = new ArrayList<>();
	private SharedInfrastructure.TestLease lease;
	private SharedInfrastructure.Configuration configuration;
	private InfrastructureSessions.Session infrastructure;
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
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				close();
			} catch (Exception failure) {
				System.err.println("Compose shutdown cleanup failed: " + failure.getClass().getSimpleName());
			}
		}, "moodly-compose-test-cleanup"));
		try {
			synchronized (this) {
				if (closed) { throw new IllegalStateException("Test session has closed"); }
				lease = SharedInfrastructure.lease(SharedInfrastructure.LOCK_FILE);
				configuration = SharedInfrastructure.configuration();
				settings.putAll(configuration.testSettings());
				database();
				index();
				infrastructure = InfrastructureSessions.forConfiguration(configuration).join(InfrastructureSessions.Kind.AUTOMATED);
			}
			infrastructure.start();
			synchronized (this) {
				if (closed) { throw new IllegalStateException("Test session has closed"); }
				initialized = true;
			}
			System.out.println("Integration tests use reserved test namespaces on shared stack moodly-local.");
		} catch (RuntimeException failure) {
			try { close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
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
		SharedInfrastructure.resetTestData(configuration);
	}

	String mongoUri(String database) {
		return "mongodb://" + settings.get("MONGODB_HOST") + ":" + settings.get("MONGODB_PORT")
				+ "/" + database + "?replicaSet=" + settings.get("MONGODB_REPLICA_SET");
	}

	String searchUri() {
		return "http://" + settings.get("ELASTICSEARCH_HOST") + ":" + settings.get("ELASTICSEARCH_PORT");
	}

	private synchronized void close() {
		if (closed) { return; }
		closed = true;
		RuntimeException failure = null;
		try {
			closeContexts();
			if (initialized) { resetData(); }
		} catch (RuntimeException cleanup) { failure = cleanup; }
		try { if (infrastructure != null) { infrastructure.close(); } }
		catch (RuntimeException cleanup) { if (failure == null) { failure = cleanup; } else { failure.addSuppressed(cleanup); } }
		try { if (lease != null) { lease.close(); } }
		catch (RuntimeException cleanup) { if (failure == null) { failure = cleanup; } else { failure.addSuppressed(cleanup); } }
		if (failure != null) { throw new IllegalStateException("Test session cleanup failed", failure); }
	}
}
