package com.tlavu.moodly.support;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.json.JsonMapper;

/** CLI entry point for developer tooling; not part of the production artifact. */
public final class DevelopmentLauncher {

	private DevelopmentLauncher() {}

	public static void main(String[] args) {
		try {
			if (args.length == 0) { throw new IllegalArgumentException("Use local, manual, up, config, docs-port or clean-test"); }
			if (args[0].equals("docs-port")) {
				System.out.println(docsPort());
				return;
			}
			var configuration = SharedInfrastructure.configuration();
			switch (args[0]) {
				case "config" -> System.out.println(JsonMapper.builder().build().writeValueAsString(configuration.testSettings()));
				case "up" -> InfrastructureSessions.forConfiguration(configuration).pin();
				case "stop", "down" -> InfrastructureSessions.forConfiguration(configuration).operator(args[0]);
				case "clean" -> {
					if (!"yes".equals(System.getenv("CONFIRM"))) { throw new IllegalStateException("Volume deletion requires CONFIRM=yes"); }
					InfrastructureSessions.forConfiguration(configuration).operator("clean");
				}
				case "local", "manual", "container", "container-test" -> System.exit(runBackend(configuration,
						args[0].equals("manual") || args[0].equals("container-test"), args.length > 1 && args[1].equals("build"), args[0].startsWith("container")));
				case "container-build" -> { SharedInfrastructure.progress("Building local backend container image..."); SharedInfrastructure.command(configuration, null, "build", "backend"); }
				case "container-stop" -> InfrastructureSessions.forConfiguration(configuration).stopBackend();
				case "clean-test" -> {
					try (var lease = SharedInfrastructure.lease(SharedInfrastructure.LOCK_FILE)) {
						System.out.print("Drop reserved MongoDB database moodly_test? Type yes: ");
						String response = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
						if ("yes".equals(response)) {
							SharedInfrastructure.command(configuration, null, "exec", "-T", "-e", "HOME=/tmp", "-e", "XDG_CONFIG_HOME=/tmp/moodly-mongosh", "mongodb", "mongosh", "--quiet",
									"--port", configuration.mongoContainerPort(), "--eval",
									"if (!db.getSiblingDB('moodly_test').dropDatabase().ok) { quit(1); }");
							System.out.println("Reserved test database cleared; search may need reindexing");
						} else { System.out.println("Cancelled; no data changed"); }
					}
				}
				default -> throw new IllegalArgumentException("Unknown developer tooling mode");
			}
		} catch (Exception failure) {
			System.err.println(failure.getMessage());
			System.exit(1);
		}
	}

	static String docsPort() throws Exception {
		Properties properties = new Properties();
		for (String filename : List.of(".env.docs.example", ".env.docs")) {
			Path path = Path.of(filename);
			if (Files.exists(path)) {
				try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { properties.load(reader); }
			}
		}
		String port = properties.getProperty("SERVER_PORT");
		SharedInfrastructure.validatePort(port);
		return port;
	}

	private static int runBackend(SharedInfrastructure.Configuration configuration, boolean manual, boolean build, boolean container) throws Exception {
		SharedInfrastructure.progress("Preparing " + (manual ? "manual test" : "local") + " backend session...");
		try (var run = new BackendRun(configuration, manual, container)) {
			run.open();
			if (build) {
				SharedInfrastructure.progress("Building backend...");
				int result = run.maven("package", "-DskipTests");
				if (result != 0) { return result; }
			}
			SharedInfrastructure.progress("Launching " + (manual ? "manual test" : "local") + " backend on port " + run.environment.get("SERVER_PORT") + "...");
			if (container) { return run.container(); }
			return run.maven("-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false", "spring-boot:run");
		}
	}

	private static final class BackendRun implements AutoCloseable {
		private final SharedInfrastructure.Configuration configuration;
		private final boolean manual;
		private final boolean container;
		private boolean containerAttempted;
		private final Map<String, String> environment;
		private InfrastructureSessions.Session infrastructure;
		private SharedInfrastructure.TestLease testLease;
		private Process child;
		private boolean ready;
		private boolean backendStarted;
		private boolean closed;

		BackendRun(SharedInfrastructure.Configuration configuration, boolean manual, boolean container) {
			this.configuration = configuration;
			this.manual = manual;
			this.container = container;
			this.environment = configuration.applicationEnvironment(manual);
			interruptResult("running");
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try { close(); } catch (RuntimeException failure) { System.err.println("Backend session cleanup failed"); }
			}, "moodly-backend-session-cleanup"));
		}

		void open() {
			SharedInfrastructure.requireFreeBackendPort(environment.get("SERVER_PORT"));
			synchronized (this) {
				if (closed) { throw new IllegalStateException("Backend session has closed"); }
				if (manual) { testLease = SharedInfrastructure.lease(SharedInfrastructure.LOCK_FILE); }
				infrastructure = InfrastructureSessions.forConfiguration(configuration).join(
						manual ? InfrastructureSessions.Kind.MANUAL : InfrastructureSessions.Kind.LOCAL);
			}
			infrastructure.start();
			synchronized (this) {
				if (closed) { throw new IllegalStateException("Backend session has closed"); }
				ready = true;
				if (manual) { SharedInfrastructure.resetTestData(configuration); }
			}
		}

		int container() throws Exception {
			synchronized (this) {
				if (closed) { throw new IllegalStateException("Backend session has closed"); }
				containerAttempted = true;
				SharedInfrastructure.progress("Starting backend container; waiting for health...");
				SharedInfrastructure.containerCommand(configuration, manual, "up", "--no-build", "--no-deps", "-d", "backend");
				backendStarted = true;
			}
			boolean healthy = false;
			for (int attempt = 0; attempt < 120; attempt++) {
				if (SharedInfrastructure.containerBackendMode(configuration).isEmpty()) { throw new IllegalStateException("Backend container exited before health readiness; inspect local-container-logs"); }
				if (SharedInfrastructure.backendHealthy()) { healthy = true; break; }
				Thread.sleep(1000);
			}
			if (!healthy) { throw new IllegalStateException("Backend container health timed out; inspect local-container-logs"); }
			SharedInfrastructure.progress("Backend container healthy; Ctrl+C stops this session. Use make local-container-logs for application logs.");
			while (!SharedInfrastructure.containerBackendMode(configuration).isEmpty()) { Thread.sleep(1000); }
			return SharedInfrastructure.backendExitCode();
		}

		int maven(String... arguments) throws Exception {
			boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
			List<String> command = new ArrayList<>(windows ? List.of("cmd.exe", "/d", "/c", "mvnw.cmd") : List.of("./mvnw"));
			command.add("--batch-mode");
			command.addAll(List.of(arguments));
			ProcessBuilder builder = new ProcessBuilder(command).inheritIO();
			builder.environment().clear();
			builder.environment().putAll(environment);
			Process running;
			synchronized (this) {
				if (closed) { throw new IllegalStateException("Backend session has closed"); }
				child = builder.start();
				if (List.of(arguments).contains("spring-boot:run")) { backendStarted = true; }
				running = child;
			}
			try { return running.waitFor(); }
			finally { synchronized (this) { stopTree(running); child = null; } }
		}

		@Override public synchronized void close() {
			if (closed) { return; }
			closed = true;
			if (backendStarted) { SharedInfrastructure.progress("Stopping backend..."); }
			RuntimeException failure = null;
			if (child != null) { stopTree(child); }
			try {
				if (container && containerAttempted) { SharedInfrastructure.command(configuration, null, "stop", "-t", "30", "backend"); }
				if (backendStarted) { SharedInfrastructure.progress("Backend stopped."); }
			} catch (RuntimeException stopFailure) { failure = stopFailure; }
			try { if (manual && ready && failure == null) { SharedInfrastructure.resetTestData(configuration); } }
			catch (RuntimeException cleanup) { if (failure == null) { failure = cleanup; } else { failure.addSuppressed(cleanup); } }
			try { if (infrastructure != null) { infrastructure.close(); } }
			catch (RuntimeException cleanup) { if (failure == null) { failure = cleanup; } else { failure.addSuppressed(cleanup); } }
			try { if (testLease != null) { testLease.close(); } }
			catch (RuntimeException cleanup) { if (failure == null) { failure = cleanup; } else { failure.addSuppressed(cleanup); } }
			if (failure != null) { SharedInfrastructure.progress("Session cleanup failed."); interruptResult("failed"); throw failure; }
			interruptResult("clean");
			SharedInfrastructure.progress((manual ? "Manual test" : "Local backend") + " session closed.");
		}
	}

	private static void interruptResult(String status) {
		String filename = System.getenv("MOODLY_TOOLING_INTERRUPT_RESULT");
		if (filename == null) { return; }
		try { Files.writeString(Path.of(filename), status, StandardCharsets.UTF_8); }
		catch (java.io.IOException failure) { throw new IllegalStateException("Could not record backend cleanup status", failure); }
	}

	private static void stopTree(Process child) {
		if (!child.isAlive()) { return; }
		List<ProcessHandle> descendants = child.descendants().toList();
		for (var process : descendants.reversed()) { process.destroy(); }
		child.destroy();
		try {
			if (!child.waitFor(20, TimeUnit.SECONDS)) { child.destroyForcibly().waitFor(); }
			for (var process : descendants) {
				if (process.isAlive()) { process.destroyForcibly(); }
			}
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			for (var process : descendants) { process.destroyForcibly(); }
			child.destroyForcibly();
		}
	}
}
