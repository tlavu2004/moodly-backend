package com.tlavu.moodly.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.tlavu.moodly.support.AutomationTooling.JSON;

final class ContainerSmoke {
	private final String network = "moodly-smoke-" + UUID.randomUUID().toString().substring(0, 12);
	private final List<String> containers = new ArrayList<>();

	static void run(String image) throws Exception {
		new ContainerSmoke().verify(image);
	}

	private void verify(String image) throws Exception {
		String mongoTag = Files.readAllLines(Path.of(".env.local.example")).stream()
				.filter(line -> line.startsWith("MONGODB_VERSION=")).findFirst().orElseThrow().split("=", 2)[1];
		try {
			docker("network", "create", network);
			String mongo = start("mongodb", "mongo:" + mongoTag, List.of("--tmpfs", "/data/db", "--tmpfs", "/data/configdb"),
					"mongod", "--replSet", "rs0", "--bind_ip_all");
			await("MongoDB ping", mongo, 180, () -> mongo(mongo, false, "quit(db.adminCommand({ping:1}).ok ? 0 : 1)"));
			mongo(mongo, true, "rs.initiate({_id:'rs0',members:[{_id:0,host:'mongodb:27017'}]})");
			await("MongoDB writable PRIMARY", mongo, 180, () -> mongo(mongo, false, "quit(db.hello().isWritablePrimary ? 0 : 1)"));
			docker("exec", mongo, "sh", "-c", "test ! -e /data/db/.mongodb");

			String search = start("elasticsearch", "docker.elastic.co/elasticsearch/elasticsearch-oss:7.10.2",
					List.of("-e", "discovery.type=single-node", "-e", "action.auto_create_index=false", "-e", "ES_JAVA_OPTS=-Xms256m -Xmx256m"));
			await("Elasticsearch ready", search, 180, () -> succeeds("exec", search, "curl", "-fsS",
					"http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=2s"));
			String oidc = start("oidc", "maven:3.9.16-eclipse-temurin-25-alpine@sha256:781f1da87cd3d142e66e47cc149c4582a652fb7f574648206d1e956170c0dc90", List.of("--mount",
					"type=bind,source=" + Path.of("src/test/resources/scripts/smoke/SmokeOidc.java").toAbsolutePath() + ",target=/tmp/SmokeOidc.java,readonly",
					"--entrypoint", "java"), "/tmp/SmokeOidc.java");
			await("OIDC discovery ready", oidc, 180, () -> succeeds("exec", search, "curl", "-fsS", "http://oidc:8000/.well-known/openid-configuration"));

			var options = new ArrayList<>(List.of("--memory", "768m"));
			for (String variable : List.of(
				"SPRING_PROFILES_ACTIVE=production",
				"MONGODB_URI=mongodb://mongodb:27017/moodly_smoke_test?replicaSet=rs0",
				"ELASTICSEARCH_URIS=http://elasticsearch:9200",
				"AUTH0_ISSUER_URI=http://oidc:8000/",
				"AUTH0_AUDIENCE=moodly-smoke",
				"CLOUDINARY_CLOUD_NAME=smoke-fixture",
				"CLOUDINARY_API_KEY=smoke-fixture",
				"CLOUDINARY_API_SECRET=smoke-fixture",
				"CLOUDINARY_UPLOAD_PRESET=smoke-fixture",
				"CLOUDINARY_FOLDER=moodly/smoke",
				"APP_CORS_ALLOWED_ORIGINS=http://localhost:3000",
				"MOODLY_ENTRIES_COLLECTION_NAME=daily_entries",
				"MOODLY_CDC_STREAM_ID=daily_entries",
				"MOODLY_CDC_ENABLED=true",
				"MOODLY_CDC_RETRY_MAX_ATTEMPTS=3",
				"MOODLY_CDC_RETRY_INITIAL_BACKOFF_MS=200",
				"MOODLY_CDC_REINDEX_BATCH_SIZE=250",
				"MOODLY_SEARCH_INDEX_NAME=daily_entries_search",
				"MOODLY_SEARCH_INDEX_MAPPING_RESOURCE=classpath:elasticsearch/daily_entries_search.json",
				"MOODLY_SEARCH_INDEX_INITIALIZATION_ENABLED=true",
				"MOODLY_RATE_LIMIT_ENABLED=true",
				"MOODLY_E2E_RESET_ENABLED=false"
			)) {
				options.addAll(List.of("-e", variable));
			}
			String backend = start("backend", image, options);
			await("backend Docker HEALTHCHECK", backend, 240,
					() -> state(backend).path("Health").path("Status").asString("").equals("healthy"));
			System.out.print(docker("exec", oidc, "java", "/tmp/SmokeOidc.java", "check"));
			docker("exec", search, "curl", "-fsS", "http://localhost:9200/daily_entries_search/_mapping");
			String user = docker("inspect", "--format", "{{.Config.User}}", backend).strip();
			if (List.of("", "root", "0").contains(user.split(":", 2)[0])) {
				throw new IllegalStateException("Backend must run as a non-root user");
			}
		} catch (Exception failure) {
			for (String container : containers) {
				try {
					var logs = DockerCommand.run(false, "logs", "--tail", "80", container);
					System.out.println("--- " + container + " ---\n" + logs.output() + logs.error());
				} catch (Exception diagnosticFailure) {
					failure.addSuppressed(diagnosticFailure);
				}
			}
			throw failure;
		} finally {
			Collections.reverse(containers);
			for (String container : containers) {
				remove("rm", "-f", container);
			}
			remove("network", "rm", network);
		}
	}

	private String start(String service, String image, List<String> options, String... command) throws Exception {
		String name = network + "-" + service;
		containers.add(name);
		var args = new ArrayList<>(List.of("run", "-d", "--name", name, "--network", network, "--network-alias", service));
		args.addAll(options);
		args.add(image);
		args.addAll(List.of(command));
		docker(args.toArray(String[]::new));
		return name;
	}

	private static boolean mongo(String container, boolean check, String query) throws Exception {
		return DockerCommand.run(check, "exec", "-e", "HOME=/tmp", "-e", "XDG_CONFIG_HOME=/tmp/moodly-mongosh",
				container, "mongosh", "--quiet", "--eval", query).code() == 0;
	}

	private static tools.jackson.databind.JsonNode state(String name) throws Exception {
		return JSON.readTree(docker("inspect", "--format", "{{json .State}}", name));
	}

	private static void await(String label, String container, int seconds, Probe probe) throws Exception {
		await(label, container, seconds, probe, () -> state(container), System::nanoTime,
				() -> Thread.sleep(Duration.ofSeconds(2)));
	}

	static void await(String label, String container, int seconds, Probe probe, StateReader inspect,
					  java.util.function.LongSupplier clock, Pause pause) throws Exception {
		long deadline = clock.getAsLong() + Duration.ofSeconds(seconds).toNanos();
		while (clock.getAsLong() < deadline) {
			var state = inspect.read();
			if (!state.path("Running").asBoolean() || state.path("Restarting").asBoolean()) {
				throw new IllegalStateException(container + " stopped or restarted before readiness, exit=" + state.path("ExitCode"));
			}
			if (probe.ready()) {
				System.out.println("PASS: " + label);
				return;
			}
			pause.sleep();
		}
		throw new IllegalStateException("Timed out waiting for " + label);
	}

	@FunctionalInterface
	interface Probe {
		boolean ready() throws Exception;
	}

	@FunctionalInterface
	interface StateReader {
		tools.jackson.databind.JsonNode read() throws Exception;
	}

	@FunctionalInterface
	interface Pause {
		void sleep() throws InterruptedException;
	}

	private static String docker(String... args) throws Exception {
		return DockerCommand.run(true, args).output();
	}

	private static boolean succeeds(String... args) throws Exception {
		return DockerCommand.run(false, args).code() == 0;
	}

	private static void remove(String... args) {
		try {
			DockerCommand.run(false, args);
		} catch (Exception failure) {
			System.err.println("Docker cleanup failed: " + failure.getMessage());
		}
	}
}
