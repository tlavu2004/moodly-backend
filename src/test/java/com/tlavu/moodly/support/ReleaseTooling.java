package com.tlavu.moodly.support;

import static com.tlavu.moodly.support.AutomationTooling.JSON;
import static com.tlavu.moodly.support.AutomationTooling.requireMatch;
import static com.tlavu.moodly.support.AutomationTooling.text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

final class ReleaseTooling {
	private static final Pattern REPOSITORY = Pattern.compile("ghcr\\.io/[a-z0-9][a-z0-9_.-]*/[a-z0-9][a-z0-9_.-]*");
	private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
	private static final Pattern IMAGE_LINE = Pattern.compile("(?m)^(\\h+url: )ghcr\\.io/\\S+$");
	private static final Pattern COMMIT_LINE = Pattern.compile("(?m)^# Image source commit: [0-9a-f]{40}$");

	@FunctionalInterface
	interface Docker {
		String run(String... args) throws Exception;
	}

	private ReleaseTooling() {}

	static void publish(String repository, String commit, JsonNode metadata, Path record, Path summary) throws Exception {
		publish(repository, commit, metadata, record, summary, args -> DockerCommand.run(true, args).output());
	}

	static void publish(String repository, String commit, JsonNode metadata, Path record, Path summary,
						Docker docker) throws Exception {
		requireMatch(repository, REPOSITORY, "Expected a specific GHCR repository");
		requireMatch(commit, COMMIT, "Expected a complete source commit");
		var tags = new ArrayList<String>();
		var tagPattern = Pattern.compile(Pattern.quote(repository) + ":[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}");
		if (!metadata.path("tags").isArray() || metadata.path("tags").isEmpty()) {
			throw new IllegalArgumentException("Expected release tags");
		}
		for (JsonNode tag : metadata.path("tags")) {
			tags.add(requireMatch(text(tag), tagPattern, "Release tags must belong to the expected repository"));
		}
		String first = tags.getFirst();
		var image = JSON.readTree(docker.run("image", "inspect", first)).path(0);
		if (!commit.equals(text(image.path("Config").path("Labels").path("org.opencontainers.image.revision")))) {
			throw new IllegalStateException("Loaded image revision does not match the verified commit");
		}
		if (!"linux".equals(text(image.path("Os"))) || !"amd64".equals(text(image.path("Architecture")))) {
			throw new IllegalStateException("Render release image must target linux/amd64");
		}
		for (String tag : tags) {
			docker.run("push", tag);
		}
		var published = JSON.readTree(docker.run("image", "inspect", first)).path(0);
		if (!image.path("Id").equals(published.path("Id"))) {
			throw new IllegalStateException("Image identity changed during publication");
		}
		var digests = new ArrayList<String>();
		for (JsonNode node : published.path("RepoDigests")) {
			if (digestPattern(repository).matcher(text(node)).matches()) {
				digests.add(text(node));
			}
		}
		if (digests.size() != 1) {
			throw new IllegalStateException("Expected exactly one published digest for the release repository");
		}
		Files.writeString(record, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
				Map.of("schemaVersion", 1, "commit", commit, "repository", repository, "tags", tags, "images", digests)) + "\n");
		Files.writeString(summary, "## Verified release image\n\nCommit: `" + commit + "`\n\n- `"
				+ digests.getFirst() + "`\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}

	static void renderFile(Path record, Path template, Path output, String repository) throws Exception {
		for (Path input : List.of(record, template)) {
			if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())
					|| (Files.exists(output) && Files.isSameFile(input, output))) {
				throw new IllegalArgumentException("Candidate output must not overwrite its inputs");
			}
		}
		Files.writeString(output, render(JSON.readTree(record), Files.readString(template), repository));
		System.out.println("Wrote offline Render candidate: " + output);
	}

	static String render(JsonNode record, String template, String repository) {
		requireMatch(repository, REPOSITORY, "Expected a specific GHCR repository");
		String commit = requireMatch(text(record.path("commit")), COMMIT, "Expected a complete source commit");
		var images = record.path("images");
		if (!images.isArray() || images.size() != 1) {
			throw new IllegalArgumentException("Expected one immutable release digest for the approved repository");
		}
		String digest = requireMatch(text(images.path(0)), digestPattern(repository),
				"Expected one immutable release digest for the approved repository");
		if (record.has("repository") && !repository.equals(text(record.path("repository")))) {
			throw new IllegalArgumentException("Release repository mismatch");
		}
		var imageLine = IMAGE_LINE.matcher(template);
		String result = replaceSingle(imageLine, imageLine.find() ? imageLine.group(1) + digest : null,
				"Blueprint must have exactly one GHCR image URL");
		var commitLine = COMMIT_LINE.matcher(result);
		return replaceSingle(commitLine, "# Image source commit: " + commit,
				"Blueprint source commit marker is missing or ambiguous");
	}

	private static Pattern digestPattern(String repository) {
		return Pattern.compile(Pattern.quote(repository) + "@sha256:[0-9a-f]{64}");
	}

	private static String replaceSingle(Matcher matcher, String replacement, String message) {
		if (replacement == null || matcher.reset().results().count() != 1) {
			throw new IllegalArgumentException(message);
		}
		return matcher.reset().replaceFirst(Matcher.quoteReplacement(replacement));
	}
}
