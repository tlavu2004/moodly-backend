package com.tlavu.moodly.support;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Development-only tooling shared by the CLI launcher and JUnit infrastructure. */
public final class SharedInfrastructure {

	public static final String TEST_DATABASE = "moodly_test";
	public static final String TEST_INDEX = "daily_entries_search_test";
	public static final Path LOCK_FILE = Path.of(".moodly-test.lock");
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Pattern ENV_KEY = Pattern.compile("^\\s*(?:export\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*[=:]");

	private SharedInfrastructure() {}

	public record Configuration(Path envFile, Map<String, String> localEnvironment,
			String mongoHost, String mongoPort, String mongoContainerPort, String replicaSet,
			String searchHost, String searchPort) {
		@Override
		public String toString() { return "SharedInfrastructure.Configuration[envFile=" + envFile + "]"; }

		public List<String> compose() {
			return List.of("docker", "compose", "-p", "moodly-backend", "--env-file", envFile.toString(), "-f", "compose.local.yaml");
		}

		public Map<String, String> testSettings() {
			return Map.of("envFile", envFile.toString(), "MONGODB_HOST", mongoHost,
					"MONGODB_PORT", mongoPort, "MONGODB_CONTAINER_PORT", mongoContainerPort,
					"MONGODB_REPLICA_SET", replicaSet, "MONGODB_DATABASE", TEST_DATABASE,
					"ELASTICSEARCH_HOST", searchHost, "ELASTICSEARCH_PORT", searchPort,
					"MOODLY_SEARCH_INDEX_NAME", TEST_INDEX);
		}

		public Map<String, String> applicationEnvironment(boolean manual) {
			Map<String, String> environment = new LinkedHashMap<>(localEnvironment);
			environment.put("MOODLY_INFRA_ENV_FILE", envFile.toString());
			environment.put("SPRING_PROFILES_ACTIVE", manual ? "compose-test" : "local");
			if (manual) {
				environment.put("MONGODB_DATABASE", TEST_DATABASE);
				environment.put("MOODLY_SEARCH_INDEX_NAME", TEST_INDEX);
			}
			validatePort(environment.get("SERVER_PORT"));
			environment.put("SPRING_MONGODB_URI", "mongodb://" + mongoHost + ":" + mongoPort + "/"
					+ environment.get("MONGODB_DATABASE") + "?replicaSet=" + replicaSet);
			environment.put("SPRING_ELASTICSEARCH_URIS", "http://" + searchHost + ":" + searchPort);
			return environment;
		}
	}

	static void progress(String message) {
		System.err.println("[tooling] " + message);
	}

	public static Path infrastructureFile() {
		String selected = System.getenv("MOODLY_INFRA_ENV_FILE");
		return Path.of(selected != null ? selected : Files.exists(Path.of(".env.local"))
				? ".env.local" : ".env.local.example").toAbsolutePath().normalize();
	}

	public static Configuration configuration() {
		Path file = infrastructureFile();
		JsonNode services = JSON.readTree(compose(file, null, "config", "--format", "json")).path("services");
		JsonNode mongo = services.path("mongodb");
		JsonNode search = services.path("elasticsearch");
		Map<String, String> environment = runtimeEnvironment(file);
		validateResources(mongo.path("labels").path("io.moodly.local-database").asText(),
				search.path("labels").path("io.moodly.local-index").asText(), TEST_DATABASE, TEST_INDEX);
		String mongoHost = environment.get("MONGODB_HOST");
		String searchHost = environment.get("ELASTICSEARCH_HOST");
		if (!Set.of("localhost", "127.0.0.1").contains(mongoHost == null ? "" : mongoHost)
				|| !Set.of("localhost", "127.0.0.1").contains(searchHost == null ? "" : searchHost)) {
			throw new IllegalStateException("Shared local/test hosts must be loopback hosts");
		}
		return new Configuration(file, Map.copyOf(environment), mongoHost, publishedPort(mongo),
				mongo.path("environment").path("MONGODB_CONTAINER_PORT").asText(),
				mongo.path("environment").path("MONGODB_REPLICA_SET").asText(), searchHost, publishedPort(search));
	}

	static String publishedPort(JsonNode service) {
		JsonNode ports = service.path("ports");
		if (ports.size() != 1 || !"127.0.0.1".equals(ports.path(0).path("host_ip").asText())) {
			throw new IllegalStateException("Shared services must publish exactly one loopback port");
		}
		return ports.path(0).path("published").asText();
	}

	public static void validateResources(String localDatabase, String localIndex, String database, String index) {
		if (!TEST_DATABASE.equals(database) || !TEST_INDEX.equals(index)) {
			throw new IllegalStateException("Only the exact reserved test database/index may be reset");
		}
		if (localDatabase == null || !localDatabase.matches("[A-Za-z0-9_-]+")
				|| localIndex == null || !localIndex.matches("[a-z0-9][a-z0-9_-]*")) {
			throw new IllegalStateException("Local resources must be explicit names without wildcards or lists");
		}
		if (localDatabase.equals(database) || localIndex.equals(index)) {
			throw new IllegalStateException("Local resources collide with reserved test resources");
		}
	}

	public static void validatePort(String value) {
		try {
			if (value == null || !value.matches("[0-9]+")) { throw new NumberFormatException(); }
			int port = Integer.parseInt(value);
			if (port >= 1 && port <= 65535) { return; }
		} catch (NumberFormatException ignored) {
			// Report only the setting name, never its contents.
		}
		throw new IllegalStateException("Set a valid SERVER_PORT in the local env file");
	}

	static Set<String> dotenvKeys(String content) {
		Set<String> keys = new LinkedHashSet<>();
		char quote = 0;
		for (String line : content.replace("\uFEFF", "").split("\\R")) {
			if (quote == 0) {
				var match = ENV_KEY.matcher(line);
				if (!match.find()) { continue; }
				keys.add(match.group(1));
				String value = line.substring(match.end()).stripLeading();
				if (value.isEmpty() || (value.charAt(0) != '\'' && value.charAt(0) != '"')) { continue; }
				quote = value.charAt(0);
				line = value.substring(1);
			}
			boolean escaped = false;
			for (char character : line.toCharArray()) {
				if (escaped) { escaped = false; }
				else if (character == '\\') { escaped = true; }
				else if (character == quote) { quote = 0; break; }
			}
		}
		return keys;
	}

	public static Map<String, String> runtimeEnvironment(Path file) {
		try {
			Map<String, String> placeholders = new LinkedHashMap<>();
			for (String key : dotenvKeys(Files.readString(file, StandardCharsets.UTF_8))) {
				placeholders.put(key, "${" + key + "}");
			}
			String definition = JSON.writeValueAsString(Map.of("services", Map.of("runtime",
					Map.of("image", "scratch", "environment", placeholders))));
			JsonNode resolved = JSON.readTree(compose(file, definition, "config", "--format", "json"))
					.path("services").path("runtime").path("environment");
			Map<String, String> environment = new LinkedHashMap<>(System.getenv());
			resolved.properties().forEach(entry -> environment.put(entry.getKey(), entry.getValue().asText().replace("$$", "$")));
			return environment;
		} catch (IOException failure) {
			throw new IllegalStateException("Could not read the selected local env file", failure);
		}
	}

	private static String compose(Path file, String definition, String... arguments) {
		List<String> invocation = new ArrayList<>(List.of("docker", "compose", "-p", "moodly-backend", "--env-file",
				file.toString(), "-f", definition == null ? "compose.local.yaml" : "-"));
		invocation.addAll(List.of(arguments));
		return execute(invocation, definition, null);
	}

	public static String command(Configuration configuration, Path input, String... arguments) {
		List<String> invocation = new ArrayList<>(configuration.compose());
		invocation.addAll(List.of(arguments));
		return execute(invocation, null, input);
	}

	private static String execute(List<String> invocation, String definition, Path input) {
		try {
			Path output = Files.createTempFile("moodly-tooling-", ".log");
			Path errors = Files.createTempFile("moodly-tooling-errors-", ".log");
			try {
				ProcessBuilder builder = new ProcessBuilder(invocation).redirectError(errors.toFile()).redirectOutput(output.toFile());
				builder.environment().keySet().removeIf(key -> key.startsWith("MONGODB_")
						|| key.startsWith("ELASTICSEARCH_") || key.equals("MOODLY_SEARCH_INDEX_NAME"));
				if (input != null) { builder.redirectInput(input.toFile()); }
				Process process = builder.start();
				if (definition != null) {
					try (var stdin = process.getOutputStream()) { stdin.write(definition.getBytes(StandardCharsets.UTF_8)); }
				} else if (input == null) { process.getOutputStream().close(); }
				if (!process.waitFor(180, TimeUnit.SECONDS)) {
					process.destroyForcibly();
					throw new IllegalStateException("Docker Compose command timed out");
				}
				if (process.exitValue() != 0) {
					// Expanded config/credential values may occur in Compose diagnostics.
					throw new IllegalStateException("Docker command failed: exit=" + process.exitValue()
							+ ", category=" + diagnosticCategory(Files.readString(errors, StandardCharsets.UTF_8)));
				}
				return Files.readString(output, StandardCharsets.UTF_8);
			} finally { Files.deleteIfExists(output); Files.deleteIfExists(errors); }
		} catch (IOException failure) {
			throw new IllegalStateException("Docker with Compose is required for development infrastructure", failure);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted waiting for Docker Compose", failure);
		}
	}

	/** Return only fixed categories; never echo command output, arguments, or credentials. */
	static String diagnosticCategory(String error) {
		String text = error.toLowerCase(java.util.Locale.ROOT);
		if (text.contains("mongodb cannot start: linux kernel") || text.contains("12257600")) { return "mongodb-kernel-incompatible"; }
		if (text.contains("executable file not found") || text.contains("command not found") || text.contains("mongosh: not found")) { return "executable-not-found"; }
		if (text.contains("not running") || text.contains("restarting")) { return "container-not-running"; }
		if (text.contains("connection refused") || text.contains("econnrefused")) { return "connection-refused"; }
		if (text.contains("permission denied")) { return "permission-denied"; }
		if (text.contains("cannot connect to the docker daemon")) { return "docker-unavailable"; }
		if (text.contains("invalid port") || text.contains("invalid argument")) { return "invalid-argument"; }
		return "unclassified";
	}

	private static void startupDiagnostics() {
		try {
			String logs = execute(List.of("docker", "logs", "--tail", "80", "moodly-mongodb-local"), null, null);
			progress("MongoDB startup log category: " + diagnosticCategory(logs));
		} catch (RuntimeException failure) { progress("MongoDB startup log category unavailable."); }
		for (String container : List.of("moodly-mongodb-local", "moodly-elasticsearch-local")) {
			try {
				String state = execute(List.of("docker", "inspect", "--format",
						"{{.State.Status}} {{.State.ExitCode}} {{.State.OOMKilled}} {{.RestartCount}}", container), null, null).trim();
				// Validate the entire response before logging even these selected fields.
				if (state.matches("[a-z]+ -?[0-9]+ (true|false) [0-9]+")) {
					progress(container + " state/exit/OOM/restarts: " + state);
				} else { progress(container + " state unavailable."); }
			} catch (RuntimeException failure) { progress(container + " inspection failed (details suppressed)."); }
		}
	}

	public static void start(Configuration configuration) {
		try { startAndAwait(configuration); }
		catch (RuntimeException failure) {
			startupDiagnostics();
			throw failure;
		}
	}

	static void validateMongoState(String state) {
		if (!"running".equals(state)) {
			throw new IllegalStateException("MongoDB container stopped or is restarting before readiness; inspect startup diagnostics");
		}
	}

	private static void startAndAwait(Configuration configuration) {
		command(configuration, null, "up", "-d", "mongodb", "elasticsearch");
		boolean ready = false;
		IllegalStateException lastFailure = null;
		for (int attempt = 0; attempt < 60; attempt++) {
			validateMongoState(execute(List.of("docker", "inspect", "--format", "{{.State.Status}}", "moodly-mongodb-local"), null, null).strip());
			try {
				command(configuration, null, "exec", "-T", "-e", "HOME=/tmp", "-e", "XDG_CONFIG_HOME=/tmp/moodly-mongosh", "mongodb", "sh", "-c",
						"mongosh --quiet --host 127.0.0.1 --port \"$MONGODB_CONTAINER_PORT\" --eval 'quit(db.adminCommand({ping:1}).ok ? 0 : 1)'");
				ready = true;
				break;
			} catch (IllegalStateException failure) {
				lastFailure = failure;
				if (attempt == 0 || attempt == 29 || attempt == 59) {
					progress("MongoDB readiness attempt " + (attempt + 1) + "/60 failed; awaiting startup.");
				}
				try { Thread.sleep(2000); }
				catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Interrupted waiting for MongoDB", interrupted);
				}
			}
		}
		if (!ready) { throw new IllegalStateException("Timed out waiting for MongoDB after 60 readiness attempts", lastFailure); }
		command(configuration, Path.of("scripts/docker/init-replica-set.js"), "exec", "-T", "-e", "HOME=/tmp", "-e", "XDG_CONFIG_HOME=/tmp/moodly-mongosh", "mongodb", "sh", "-c",
				"exec mongosh --quiet --host 127.0.0.1 --port \"$MONGODB_CONTAINER_PORT\" --file /dev/stdin");
		command(configuration, null, "up", "-d", "--wait", "--wait-timeout", "120", "mongodb", "elasticsearch");
	}

	public static void resetTestData(Configuration configuration) {
		validateResources(configuration.localEnvironment().get("MONGODB_DATABASE"),
				configuration.localEnvironment().get("MOODLY_SEARCH_INDEX_NAME"), TEST_DATABASE, TEST_INDEX);
		progress("Resetting reserved test database and search index...");
		String uri = "mongodb://" + configuration.mongoHost() + ":" + configuration.mongoPort() + "/" + TEST_DATABASE
				+ "?replicaSet=" + configuration.replicaSet() + "&serverSelectionTimeoutMS=10000&connectTimeoutMS=5000";
		try (var mongo = com.mongodb.client.MongoClients.create(uri);
				var http = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()) {
			mongo.getDatabase(TEST_DATABASE).drop();
			var response = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://"
					+ configuration.searchHost() + ":" + configuration.searchPort() + "/" + TEST_INDEX))
					.timeout(java.time.Duration.ofSeconds(10)).DELETE().build(), java.net.http.HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() != 200 && response.statusCode() != 404) {
				throw new IllegalStateException("Could not reset reserved test index: HTTP " + response.statusCode());
			}
			progress("Reserved test data cleared; local data preserved.");
		} catch (IOException failure) { throw new IllegalStateException("Could not reset reserved test resources", failure); }
		catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted resetting reserved test resources", failure);
		}
	}

	public static TestLease lease(Path file) {
		try {
			FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			try {
				FileLock lock = channel.tryLock();
				if (lock == null) { throw new IllegalStateException("Test resources are in use"); }
				return new TestLease(channel, lock);
			} catch (RuntimeException | IOException failure) {
				channel.close();
				if (failure instanceof OverlappingFileLockException) {
					throw new IllegalStateException("Test resources are in use", failure);
				}
				throw failure;
			}
		} catch (IOException failure) { throw new IllegalStateException("Could not acquire the test lease", failure); }
	}

	public record TestLease(FileChannel channel, FileLock lock) implements AutoCloseable {
		@Override
		public void close() {
			try {
				if (lock.isValid()) { lock.release(); }
				channel.close();
			} catch (IOException failure) { throw new IllegalStateException("Could not release the test lease", failure); }
		}
	}
}
