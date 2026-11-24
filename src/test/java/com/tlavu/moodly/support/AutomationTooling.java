package com.tlavu.moodly.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class AutomationTooling {
	static final JsonMapper JSON = JsonMapper.builder().build();

	private AutomationTooling() {}

	static void run(String[] args) throws Exception {
		switch (args[1]) {
			case "normalize-openapi" -> OpenApiTooling.normalize(Path.of(args[2]), Path.of(args[3]));
			case "validate-openapi" -> OpenApiTooling.validate(Path.of(args[2]));
			case "summarize-coverage" -> CoverageSummary.run();
			case "container-smoke" -> ContainerSmoke.run(args[2]);
			case "publish-release" -> ReleaseTooling.publish(
					System.getenv("REGISTRY") + "/" + System.getenv("IMAGE_NAME").toLowerCase(Locale.ROOT),
					System.getenv("GITHUB_SHA"), JSON.readTree(System.getenv("IMAGE_METADATA")),
					Path.of("release-image.json"), Path.of(System.getenv("GITHUB_STEP_SUMMARY")));
			case "render-candidate" -> ReleaseTooling.renderFile(
					Path.of(args[2]), Path.of(args[3]), Path.of(args[4]), args[5]);
			default -> throw new IllegalArgumentException("Unknown automation tooling mode");
		}
	}

	static String requireMatch(String value, Pattern pattern, String message) {
		if (!pattern.matcher(value).matches()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	static String text(JsonNode node) {
		return node.isString() ? node.asString() : "";
	}

	static void appendSummary(String text) throws Exception {
		Files.writeString(Path.of(System.getenv("GITHUB_STEP_SUMMARY")), text,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}
}
