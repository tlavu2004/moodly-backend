package com.tlavu.moodly.support;

import static com.tlavu.moodly.support.AutomationTooling.JSON;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

final class OpenApiTooling {
	private OpenApiTooling() {}

	static void normalize(Path input, Path output) throws Exception {
		var document = (ObjectNode) JSON.readTree(input);
		var tags = new ArrayList<JsonNode>();
		document.path("tags").forEach(tags::add);
		tags.sort(Comparator.comparing(tag -> tag.path("name").asString()));
		document.putArray("tags").addAll(tags);
		for (JsonNode path : document.path("paths")) {
			for (JsonNode operation : path) {
				if (operation instanceof ObjectNode object && object.path("responses") instanceof ObjectNode responses) {
					var sorted = JSON.createObjectNode();
					responses.properties().stream().sorted(java.util.Map.Entry.comparingByKey())
							.forEach(entry -> sorted.set(entry.getKey(), entry.getValue()));
					object.set("responses", sorted);
				}
			}
		}
		// Preserve the repository's existing two-space JSON formatting byte for byte.
		var indent = new tools.jackson.core.util.DefaultIndenter("  ", "\n");
		var printer = new tools.jackson.core.util.DefaultPrettyPrinter()
				.withObjectIndenter(indent).withArrayIndenter(indent)
				.withSeparators(tools.jackson.core.util.Separators.createDefaultInstance()
						.withObjectNameValueSpacing(tools.jackson.core.util.Separators.Spacing.AFTER)
						.withObjectEmptySeparator("").withArrayEmptySeparator(""));
		Files.writeString(output, JSON.writer().with(printer).writeValueAsString(document) + "\n");
	}

	static void validate(Path input) throws Exception {
		var document = JSON.readTree(input);
		var paths = document.path("paths");
		if (!document.path("openapi").asString("").startsWith("3.") || !paths.isObject() || paths.isEmpty()) {
			throw new IllegalArgumentException("Expected an OpenAPI 3 document with paths");
		}
		System.out.printf("Validated %s: %d paths%n", input, paths.size());
	}
}
