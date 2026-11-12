package com.tlavu.moodly.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class SharedInfrastructureTest {

	@TempDir Path temporary;

	@Test
	void diagnosticCategoriesNeverEchoSensitiveOrUnknownOutput() {
		assertThat(SharedInfrastructure.diagnosticCategory("password=synthetic-secret Connection refused"))
				.isEqualTo("connection-refused");
		assertThat(SharedInfrastructure.diagnosticCategory("Container synthetic-secret is not running"))
				.isEqualTo("container-not-running");
		assertThat(SharedInfrastructure.diagnosticCategory("mongosh: not found; synthetic-secret"))
				.isEqualTo("executable-not-found");
		assertThat(SharedInfrastructure.diagnosticCategory("MongoDB cannot start: Linux kernel versions 6.19 and newer synthetic-secret"))
				.isEqualTo("mongodb-kernel-incompatible");
		assertThat(SharedInfrastructure.diagnosticCategory("unexpected synthetic-secret"))
				.isEqualTo("unclassified");
	}

	@Test
	void acceptsDistinctLocalResources() {
		SharedInfrastructure.validateResources("moodly", "daily_entries_search", "moodly_test", "daily_entries_search_test");
	}

	@Test
	void rejectsLocalTestCollisions() {
		assertThatIllegalStateException().isThrownBy(() -> SharedInfrastructure.validateResources(
				"moodly_test", "daily_entries_search", "moodly_test", "daily_entries_search_test"));
		assertThatIllegalStateException().isThrownBy(() -> SharedInfrastructure.validateResources(
				"moodly", "daily_entries_search_test", "moodly_test", "daily_entries_search_test"));
	}

	@Test
	void rejectsWildcardAndMissingLocalResources() {
		for (String index : new String[] {"", "*", "_all", "local,test", "local/test"}) {
			assertThatIllegalStateException().isThrownBy(() -> SharedInfrastructure.validateResources(
					"moodly", index, "moodly_test", "daily_entries_search_test"));
		}
	}

	@Test
	void refusesUnknownCleanupTargets() {
		assertThatIllegalStateException().isThrownBy(() -> SharedInfrastructure.validateResources(
				"moodly", "daily_entries_search", "moodly_test_backup", "daily_entries_search_test"));
		assertThatIllegalStateException().isThrownBy(() -> SharedInfrastructure.validateResources(
				"moodly", "daily_entries_search", "moodly_test", "*"));
	}

	@Test
	void scansKeysWithoutTreatingMultilineContentsAsAssignments() {
		assertThat(SharedInfrastructure.dotenvKeys("""
				FIRST="line one
				NOT_A_KEY=fixture
				last line"
				SECOND=ok
				export THIRD: ok
				""")).isEqualTo(Set.of("FIRST", "SECOND", "THIRD"));
	}

	@Test
	void validatesHttpPortsWithoutPrintingInvalidValues() {
		for (String value : new String[] {null, "", "0", "65536", "+8080", "-1", "not-a-port"}) {
			assertThatIllegalStateException().isThrownBy(() -> SharedInfrastructure.validatePort(value));
		}
		SharedInfrastructure.validatePort("8080");
		SharedInfrastructure.validatePort("8081");
	}

	@Test
	void manualEnvironmentPreservesCustomKeysAndOverridesOnlyTestIdentity() {
		var configuration = new SharedInfrastructure.Configuration(Path.of("synthetic.env"), Map.of(
				"SERVER_PORT", "8080", "MONGODB_DATABASE", "moodly",
				"MOODLY_SEARCH_INDEX_NAME", "daily_entries_search", "CUSTOM_FEATURE", "literal $value | fixture"),
				"localhost", "27017", "27017", "rs0", "localhost", "9200");
		var local = configuration.applicationEnvironment(false);
		var manual = configuration.applicationEnvironment(true);
		assertThat(local).containsEntry("SERVER_PORT", "8080").containsEntry("MONGODB_DATABASE", "moodly");
		assertThat(manual).containsEntry("SERVER_PORT", "8080").containsEntry("MONGODB_DATABASE", "moodly_test")
				.containsEntry("MOODLY_SEARCH_INDEX_NAME", "daily_entries_search_test")
				.containsEntry("CUSTOM_FEATURE", "literal $value | fixture");
		assertThat(configuration.toString()).doesNotContain("literal", "CUSTOM_FEATURE");
	}

	@Test
	void leaseRejectsContentionAndCanBeReacquiredAfterRelease() {
		Path file = temporary.resolve("lease.lock");
		try (var lease = SharedInfrastructure.lease(file)) {
			assertThatIllegalStateException().isThrownBy(() -> SharedInfrastructure.lease(file));
		}
		try (var next = SharedInfrastructure.lease(file)) { assertThat(next.lock().isValid()).isTrue(); }
	}

	@Test
	void rejectsPublicOrMultiplePublishedPorts() {
		var json = JsonMapper.builder().build();
		assertThatIllegalStateException().isThrownBy(() -> SharedInfrastructure.publishedPort(json.readTree(
				"{\"ports\":[{\"host_ip\":\"0.0.0.0\",\"published\":\"27017\"}]}")));
		assertThatIllegalStateException().isThrownBy(() -> SharedInfrastructure.publishedPort(json.readTree("{\"ports\":[]}")));
	}
}
