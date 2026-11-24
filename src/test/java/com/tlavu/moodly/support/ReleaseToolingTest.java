package com.tlavu.moodly.support;

import static com.tlavu.moodly.support.AutomationTooling.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseToolingTest {
	private static final String REPO = "ghcr.io/example/moodly";
	private static final String COMMIT = "a".repeat(40);
	private static final String DIGEST = REPO + "@sha256:" + "b".repeat(64);

	@TempDir Path directory;

	@Test
	void publishesVerifiedTagsAndRecordsTheirDigest() throws Exception {
		var calls = publish(REPO + ":latest", COMMIT, "amd64");
		assertThat(calls).containsExactly("image", "push", "image");
		var record = JSON.readTree(directory.resolve("record.json"));
		assertThat(record.path("commit").asString()).isEqualTo(COMMIT);
		assertThat(record.path("repository").asString()).isEqualTo(REPO);
		assertThat(record.path("images").path(0).asString()).isEqualTo(DIGEST);
	}

	@Test
	void rejectsInvalidMetadataBeforePushing() {
		assertThatThrownBy(() -> publish("ghcr.io/other/moodly:latest", COMMIT, "amd64")).hasMessageContaining("expected repository");
		assertThatThrownBy(() -> publish(REPO + ":latest", "c".repeat(40), "amd64")).hasMessageContaining("revision");
		assertThatThrownBy(() -> publish(REPO + ":latest", COMMIT, "arm64")).hasMessageContaining("linux/amd64");
		assertThat(directory.resolve("record.json")).doesNotExist();
	}

	@Test
	void rendersOnlyTheApprovedDigestAndCommit() {
		String template = "# Image source commit: " + "c".repeat(40) + "\n  url: " + REPO + ":previous\n";
		var release = JSON.valueToTree(Map.of("commit", COMMIT, "images", List.of(DIGEST)));
		assertThat(ReleaseTooling.render(release, template, REPO))
				.isEqualTo("# Image source commit: " + COMMIT + "\n  url: " + DIGEST + "\n");
		assertThatThrownBy(() -> ReleaseTooling.render(release, template + template, REPO)).hasMessageContaining("exactly one");
		assertThatThrownBy(() -> ReleaseTooling.render(release, "  url: " + REPO + ":previous\n", REPO)).hasMessageContaining("marker");
		var mutable = JSON.valueToTree(Map.of("commit", COMMIT, "images", List.of(REPO + ":latest")));
		assertThatThrownBy(() -> ReleaseTooling.render(mutable, template, REPO)).hasMessageContaining("immutable");
	}

	private List<String> publish(String tag, String revision, String architecture) throws Exception {
		var calls = new ArrayList<String>();
		var metadata = JSON.valueToTree(Map.of("tags", List.of(tag)));
		var image = Map.of("Id", "image", "Os", "linux", "Architecture", architecture,
				"Config", Map.of("Labels", Map.of("org.opencontainers.image.revision", revision)), "RepoDigests", List.of(DIGEST));
		ReleaseTooling.publish(REPO, COMMIT, metadata, directory.resolve("record.json"), directory.resolve("summary.md"), args -> {
			calls.add(args[0]);
			if (args[0].equals("push")) {
				assertThat(tag).startsWith(REPO + ":");
				assertThat(revision).isEqualTo(COMMIT);
				assertThat(architecture).isEqualTo("amd64");
				return "";
			}
			return JSON.writeValueAsString(List.of(image));
		});
		return calls;
	}
}
