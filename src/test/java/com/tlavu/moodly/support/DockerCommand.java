package com.tlavu.moodly.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class DockerCommand {
	record Result(int code, String output, String error) {}

	private DockerCommand() {}

	static Result run(boolean check, String... args) throws Exception {
		var command = new ArrayList<>(List.of("docker"));
		command.addAll(List.of(args));
		Process process = new ProcessBuilder(command).start();
		process.getOutputStream().close();
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var output = executor.submit(() -> new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
			var error = executor.submit(() -> new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
			try {
				if (!process.waitFor(180, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Docker command timed out: " + args[0]);
				}
				var result = new Result(process.exitValue(), output.get(), error.get());
				if (check && result.code() != 0) {
					throw new IllegalStateException("Docker " + args[0] + " failed: " + result.error().strip());
				}
				return result;
			} finally {
				if (process.isAlive()) {
					process.destroyForcibly();
				}
			}
		}
	}
}
