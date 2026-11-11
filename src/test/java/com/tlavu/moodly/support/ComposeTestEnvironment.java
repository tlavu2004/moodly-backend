package com.tlavu.moodly.support;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.tlavu.moodly.modules.search.listener.DailyEntryChangeStreamListener;
import java.util.concurrent.TimeUnit;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reserved test namespaces on shared local infrastructure; never owns its lifecycle. */
// Keep lambdas for JDT method-reference nullability compatibility.
@SuppressWarnings("Convert2MethodRef")
final class ComposeTestEnvironment {
	private static final int MAX_MONGO_PING_ATTEMPTS = 60;
	private static ComposeTestEnvironment instance;
	private final List<String> compose;
	private final Map<String, String> settings = new LinkedHashMap<>();
	private final List<ConfigurableApplicationContext> contexts = new ArrayList<>();
	private FileChannel lockChannel;
	private FileLock lock;
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
		try {
			Files.createDirectories(Path.of("target"));
			lockChannel = FileChannel.open(Path.of(".moodly-test.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			lock = lockChannel.tryLock();
			if (lock == null) {
				lockChannel.close();
				throw new IllegalStateException("Test resources are in use. Stop the other manual/automated test session.");
			}
		} catch (IOException failure) {
			throw new IllegalStateException("Could not lock reserved test resources", failure);
		}
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				close();
			} catch (Exception failure) {
				System.err.println("Compose shutdown cleanup failed: " + failure.getClass().getSimpleName());
			}
		}, "moodly-compose-test-cleanup"));
		try {
			String python = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "python" : "python3";
			JsonNode configuration = JsonMapper.builder().build().readTree(execute(null,
					List.of(python, "scripts/docker/shared-infrastructure.py", "config")));
			for (String key : List.of("MONGODB_HOST", "MONGODB_PORT", "MONGODB_REPLICA_SET", "MONGODB_DATABASE",
					"ELASTICSEARCH_HOST", "ELASTICSEARCH_PORT", "MOODLY_SEARCH_INDEX_NAME")) {
				settings.put(key, configuration.get(key).asText());
			}
			compose = List.of("docker", "compose", "-p", "moodly-local", "--env-file",
					configuration.get("envFile").asText(), "-f", "compose.local.yaml");
			database();
			index();
			command(null, "up", "-d", "mongodb", "elasticsearch");
			boolean ready = false;
			for (int attempt = 0; attempt < MAX_MONGO_PING_ATTEMPTS; attempt++) {
				try {
					command(null, "exec", "-T", "mongodb", "sh", "-c",
							"mongosh --quiet --host 127.0.0.1 --port \"$MONGODB_CONTAINER_PORT\" --eval 'quit(db.adminCommand({ping:1}).ok ? 0 : 1)'");
					ready = true;
					break;
				} catch (IllegalStateException failure) {
					try {
						Thread.sleep(2000);
					} catch (InterruptedException interrupted) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException("Interrupted waiting for Compose MongoDB", interrupted);
					}
				}
			}
			if (!ready) {
				throw new IllegalStateException("Timed out waiting for Compose MongoDB");
			}
			command(Path.of("scripts/docker/init-replica-set.js"), "exec", "-T", "mongodb", "sh", "-c",
					"exec mongosh --quiet --host 127.0.0.1 --port \"$MONGODB_CONTAINER_PORT\" --file /dev/stdin");
			command(null, "up", "-d", "--wait", "--wait-timeout", "120", "mongodb", "elasticsearch");
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
			try {
				if (lock != null && lock.isValid()) { lock.release(); }
				if (lockChannel != null) { lockChannel.close(); }
			} catch (IOException failure) {
				failed = true;
			}
		}
		if (failed) {
			throw new IllegalStateException("Test cleanup failed; shared infrastructure was left running.");
		}
	}

	private String command(Path input, String... arguments) {
		List<String> invocation = new ArrayList<>(compose);
		invocation.addAll(List.of(arguments));
		return execute(input, invocation);
	}

	private static String execute(Path input, List<String> invocation) {
		try {
			Path output = Files.createTempFile("moodly-compose-", ".log");
			try {
				ProcessBuilder builder = new ProcessBuilder(invocation).redirectErrorStream(true).redirectOutput(output.toFile());
				builder.environment().keySet().removeIf(key -> key.startsWith("MONGODB_")
						|| key.startsWith("ELASTICSEARCH_") || key.equals("MOODLY_SEARCH_INDEX_NAME"));
				if (input != null) {
					builder.redirectInput(input.toFile());
				}
				Process process = builder.start();
				if (!process.waitFor(180, TimeUnit.SECONDS)) {
					process.destroyForcibly();
					throw new IllegalStateException("Docker Compose command timed out");
				}
				String result = Files.readString(output);
				if (process.exitValue() != 0) {
					throw new IllegalStateException("Docker Compose failed: " + String.join(" ", invocation) + "\n" + result);
				}
				return result;
			} finally {
				Files.deleteIfExists(output);
			}
		} catch (IOException failure) {
			throw new IllegalStateException("Docker Desktop with Compose is required for integration tests", failure);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted waiting for Docker Compose", failure);
		}
	}
}
