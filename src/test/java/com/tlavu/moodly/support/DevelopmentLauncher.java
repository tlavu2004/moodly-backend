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
				case "up" -> SharedInfrastructure.start(configuration);
				case "local" -> System.exit(runBackend(configuration, false, args.length > 1 && args[1].equals("build")));
				case "manual" -> {
					int result;
					try (var lease = SharedInfrastructure.lease(SharedInfrastructure.LOCK_FILE)) {
						result = runBackend(configuration, true, args.length > 1 && args[1].equals("build"));
					}
					System.exit(result);
				}
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
		Map<String, String> environment = configuration.applicationEnvironment(manual);
		SharedInfrastructure.start(configuration);
		if (build) {
			int result = maven(environment, "package", "-DskipTests");
			if (result != 0) { return result; }
		}
		return maven(environment, "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false", "spring-boot:run");
	}

	private static int maven(Map<String, String> environment, String... arguments) throws Exception {
		boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
		List<String> command = new ArrayList<>(windows ? List.of("cmd.exe", "/d", "/c", "mvnw.cmd") : List.of("./mvnw"));
		command.add("--batch-mode");
		command.addAll(List.of(arguments));
		ProcessBuilder builder = new ProcessBuilder(command).inheritIO();
		builder.environment().clear();
		builder.environment().putAll(environment);
		Process child = builder.start();
		Thread shutdown = new Thread(() -> stopTree(child), "moodly-backend-shutdown");
		Runtime.getRuntime().addShutdownHook(shutdown);
		try { return child.waitFor(); }
		finally {
			stopTree(child);
			Runtime.getRuntime().removeShutdownHook(shutdown);
		}
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
