package com.tlavu.moodly.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class InfrastructureSessionsTest {

	@TempDir Path temporary;
	private static final Set<String> BOTH = Set.of("mongodb", "elasticsearch");

	private static final class FakeServices implements InfrastructureSessions.Services {
		final Set<String> running = new LinkedHashSet<>();
		final List<Set<String>> stops = new ArrayList<>();
		String backendMode = "";
		@Override public String backendMode() { return backendMode; }
		@Override public void stopBackend() { backendMode = ""; }
		boolean failStart;
		boolean failStop;
		@Override public Set<String> running() { return Set.copyOf(running); }
		@Override public void start() {
			running.addAll(BOTH);
			if (failStart) { throw new IllegalStateException("Synthetic readiness failure"); }
		}
		@Override public void stop(Set<String> services) {
			stops.add(services);
			if (failStop) { throw new IllegalStateException("Synthetic stop failure"); }
			running.removeAll(services);
		}
		@Override public void operator(String action) { running.clear(); }
	}

	private InfrastructureSessions registry(FakeServices services) { return new InfrastructureSessions(temporary, services); }

	@Test
	void orphanedLocalContainerPreservesDependenciesAndBlocksAnotherBackend() {
		var services = new FakeServices();
		var registry = registry(services);
		var launcher = registry.join(InfrastructureSessions.Kind.LOCAL);
		launcher.start();
		services.backendMode = "local";
		launcher.close();
		assertThat(services.running).isEqualTo(BOTH);
		assertThatIllegalStateException().isThrownBy(() -> registry.join(InfrastructureSessions.Kind.LOCAL));
		assertThatIllegalStateException().isThrownBy(() -> registry.operator("stop"));
		try (var tests = registry.join(InfrastructureSessions.Kind.AUTOMATED)) { tests.start(); }
		assertThat(services.running).isEqualTo(BOTH);
		registry.stopBackend();
		assertThat(services.running).isEmpty();
	}

	@Test
	void orphanedManualContainerBlocksAutomatedResetUntilExplicitRecovery() {
		var services = new FakeServices();
		services.backendMode = "manual";
		var registry = registry(services);
		assertThatIllegalStateException().isThrownBy(() -> registry.join(InfrastructureSessions.Kind.AUTOMATED));
		assertThatIllegalStateException().isThrownBy(() -> registry.operator("down"));
		registry.stopBackend();
		try (var tests = registry.join(InfrastructureSessions.Kind.AUTOMATED)) { tests.start(); }
	}

	@Test
	void containerRecoveryCannotStopAnActiveLauncher() {
		var services = new FakeServices();
		var registry = registry(services);
		try (var launcher = registry.join(InfrastructureSessions.Kind.LOCAL)) {
			assertThatIllegalStateException().isThrownBy(registry::stopBackend);
		}
	}

	@Test
	void preexistingInfrastructureIsNotStopped() {
		var services = new FakeServices();
		services.running.addAll(BOTH);
		try (var session = registry(services).join(InfrastructureSessions.Kind.AUTOMATED)) { session.start(); }
		assertThat(services.running).isEqualTo(BOTH);
		assertThat(services.stops).isEmpty();
	}

	@Test
	void automaticallyStartedInfrastructureStopsWhenLastSessionEnds() {
		var services = new FakeServices();
		try (var session = registry(services).join(InfrastructureSessions.Kind.AUTOMATED)) {
			session.start();
			assertThat(services.running).isEqualTo(BOTH);
		}
		assertThat(services.running).isEmpty();
		assertThat(services.stops).containsExactly(BOTH);
	}

	@Test
	void overlappingSessionsKeepInfrastructureUntilTheFinalConsumerFinishes() {
		var services = new FakeServices();
		var registry = registry(services);
		var first = registry.join(InfrastructureSessions.Kind.LOCAL);
		first.start();
		var second = registry.join(InfrastructureSessions.Kind.AUTOMATED);
		second.start();
		first.close();
		assertThat(services.running).isEqualTo(BOTH);
		assertThat(services.stops).isEmpty();
		second.close();
		assertThat(services.running).isEmpty();
	}

	@Test
	void partialPreexistingInfrastructureOnlyStopsTheServiceStartedByTooling() {
		var services = new FakeServices();
		services.running.add("mongodb");
		try (var session = registry(services).join(InfrastructureSessions.Kind.AUTOMATED)) { session.start(); }
		assertThat(services.running).containsExactly("mongodb");
		assertThat(services.stops).containsExactly(Set.of("elasticsearch"));
	}

	@Test
	void explicitUpTakesOwnershipEvenWhileAnAutomaticSessionIsActive() {
		var services = new FakeServices();
		var registry = registry(services);
		try (var session = registry.join(InfrastructureSessions.Kind.AUTOMATED)) {
			session.start();
			registry.pin();
		}
		assertThat(services.running).isEqualTo(BOTH);
		assertThat(services.stops).isEmpty();
	}

	@Test
	void operatorStopIsRejectedUntilAllSessionsFinishAndClearsThePin() {
		var services = new FakeServices();
		var registry = registry(services);
		registry.pin();
		try (var session = registry.join(InfrastructureSessions.Kind.LOCAL)) {
			session.start();
			assertThatIllegalStateException().isThrownBy(() -> registry.operator("stop"));
			assertThatIllegalStateException().isThrownBy(() -> registry.operator("down"));
			assertThatIllegalStateException().isThrownBy(() -> registry.operator("clean"));
		}
		registry.operator("stop");
		try (var session = registry.join(InfrastructureSessions.Kind.AUTOMATED)) { session.start(); }
		assertThat(services.running).isEmpty();
	}

	@Test
	void startupFailureReleasesTheSessionAndStopsOnlyOwnedServices() {
		var services = new FakeServices();
		services.running.add("mongodb");
		services.failStart = true;
		try (var session = registry(services).join(InfrastructureSessions.Kind.AUTOMATED)) {
			assertThatIllegalStateException().isThrownBy(session::start);
		}
		assertThat(services.running).containsExactly("mongodb");
		assertThat(services.stops).containsExactly(Set.of("elasticsearch"));
	}

	@Test
	void localAndManualBackendsCannotClaimTheSharedHttpPortTogether() {
		var registry = registry(new FakeServices());
		try (var first = registry.join(InfrastructureSessions.Kind.LOCAL)) {
			assertThatIllegalStateException().isThrownBy(() -> registry.join(InfrastructureSessions.Kind.MANUAL));
			assertThatIllegalStateException().isThrownBy(() -> registry.join(InfrastructureSessions.Kind.LOCAL));
		}
		try (var next = registry.join(InfrastructureSessions.Kind.MANUAL)) { next.start(); }
	}

	@Test
	void stalePidRecordsArePrunedAndTheirAutomaticOwnershipIsRecovered() throws Exception {
		var services = new FakeServices();
		services.running.addAll(BOTH);
		var state = new InfrastructureSessions.State();
		state.automatic.addAll(BOTH);
		state.participants.put("stale", new InfrastructureSessions.Participant(2147483647L, "stale-start", InfrastructureSessions.Kind.LOCAL));
		Files.writeString(temporary.resolve("state.json"), JsonMapper.builder().build().writeValueAsString(state));
		try (var session = registry(services).join(InfrastructureSessions.Kind.AUTOMATED)) { session.start(); }
		assertThat(services.running).isEmpty();
	}

	@Test
	void repeatedCloseDoesNotStopServicesTwice() {
		var services = new FakeServices();
		var session = registry(services).join(InfrastructureSessions.Kind.AUTOMATED);
		session.start();
		session.close();
		session.close();
		assertThat(services.stops).containsExactly(BOTH);
	}

	@Test
	void failedStopRemainsRecordedForCleanupByTheNextSession() {
		var services = new FakeServices();
		var registry = registry(services);
		var first = registry.join(InfrastructureSessions.Kind.AUTOMATED);
		first.start();
		services.failStop = true;
		assertThatIllegalStateException().isThrownBy(first::close);
		services.failStop = false;
		try (var next = registry.join(InfrastructureSessions.Kind.AUTOMATED)) { next.start(); }
		assertThat(services.running).isEmpty();
		assertThat(services.stops).hasSize(2);
	}
}
