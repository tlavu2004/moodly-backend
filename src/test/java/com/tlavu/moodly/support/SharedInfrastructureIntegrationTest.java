package com.tlavu.moodly.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the real Compose dotenv parser, without starting containers or resetting data. */
class SharedInfrastructureIntegrationTest {

	@TempDir Path temporary;

	@Test
	void composeRoundTripPreservesAllRuntimeValues() throws Exception {
		Path file = temporary.resolve("synthetic.env");
		Files.writeString(file, """
				SERVER_PORT=18081
				SPRING_THREADS_VIRTUAL_ENABLED=true
				CUSTOM_SUBJECT='synthetic|fixture'
				CUSTOM_LITERAL='literal $$value $word | punctuation'
				CUSTOM_MULTILINE="first
				second"
				""", StandardCharsets.UTF_8);
		var environment = SharedInfrastructure.runtimeEnvironment(file);
		assertThat(environment.get("SERVER_PORT")).isEqualTo("18081");
		assertThat(environment.get("SPRING_THREADS_VIRTUAL_ENABLED")).isEqualTo("true");
		assertThat(environment.get("CUSTOM_SUBJECT")).isEqualTo("synthetic|fixture");
		assertThat(environment.get("CUSTOM_LITERAL")).isEqualTo("literal $$value $word | punctuation");
		assertThat(environment.get("CUSTOM_MULTILINE")).isEqualTo("first\nsecond");
	}
}
