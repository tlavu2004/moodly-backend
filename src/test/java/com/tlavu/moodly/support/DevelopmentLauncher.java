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
				case "local", "manual" -> System.exit(runBackend(configuration, args[0].equals("manual"), args.length > 1 && args[1].equals("build")));
				case "clean-test" -> {
					try (var lease = SharedInfrastructure.lease(SharedInfrastructure.LOCK_FILE)) {
						System.out.print("Drop reserved MongoDB database moodly_test? Type yes: ");
						String response = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
						if ("yes".equals(response)) {
							SharedInfrastructure.command(configuration, null, "exec", "-T", "mongodb", "mongosh", "--quiet",
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

	private static int runBackend(SharedInfrastructure.Configuration configuration, boolean manual, boolean build) throws Exception {
		SharedInfrastructure.progress("Preparing " + (manual ? "manual test" : "local") + " backend session...");
		try (var run = new BackendRun(configuration, manual)) {
			run.open();
			if (build) {
				SharedInfrastructure.progress("Building backend...");
				int result = run.maven("package", "-DskipTests");
				if (result != 0) { return result; }
			}
			SharedInfrastructure.progress("Launching " + (manual ? "manual test" : "local") + " backend on port " + run.environment.get("SERVER_PORT") + "...");
			return run.maven("-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false", "spring-boot:run");
		}
	}

	private static final class BackendRun implements AutoCloseable {
		private final SharedInfrastructure.Configuration configuration;
		private final boolean manual;
		private final Map<String, String> environment;
		private InfrastructureSessions.Session infrastructure;
		private SharedInfrastructure.TestLease testLease;
		private Process child;
		private boolean ready;
		private boolean backendStarted;
		private boolean closed;

		BackendRun(SharedInfrastructure.Configuration configuration, boolean manual) {
			this.configuration = configuration;
			this.manual = manual;
			this.environment = configuration.applicationEnvironment(manual);
			interruptResult("running");
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try { close(); } catch (RuntimeException failure) { System.err.println("Backend session cleanup failed"); }
			}, "moodly-backend-session-cleanup"));
		}

		void open() {
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
			if (child != null) { stopTree(child); }
			if (backendStarted) { SharedInfrastructure.progress("Backend stopped."); }
			RuntimeException failure = null;
			try { if (manual && ready) { SharedInfrastructure.resetTestData(configuration); } }
			catch (RuntimeException cleanup) { failure = cleanup; }
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
