package com.tlavu.moodly.support;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/** Cross-process ownership registry. It never removes containers, networks or volumes. */
public final class InfrastructureSessions {

	public enum Kind { LOCAL, MANUAL, AUTOMATED }

	interface Services {
		Set<String> running();
		void start();
		void stop(Set<String> services);
		void operator(String action);
	}

	private static final Set<String> SERVICES = Set.of("mongodb", "elasticsearch");
	private static final Object JVM_GUARD = new Object();
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private final Path directory;
	private final Services services;

	public record Participant(long pid, String startedAt, Kind kind) {}

	public static final class State {
		public boolean pinned;
		public Set<String> automatic = new LinkedHashSet<>();
		public Map<String, Participant> participants = new LinkedHashMap<>();
	}

	InfrastructureSessions(Path directory, Services services) {
		this.directory = directory;
		this.services = services;
	}

	public static InfrastructureSessions forConfiguration(SharedInfrastructure.Configuration configuration) {
		return new InfrastructureSessions(Path.of(".moodly-infrastructure"), new Services() {
			@Override public Set<String> running() {
				Set<String> result = new LinkedHashSet<>();
				SharedInfrastructure.command(configuration, null, "ps", "--services", "--status", "running")
						.lines().map(String::strip).filter(SERVICES::contains).forEach(result::add);
				return result;
			}
			@Override public void start() { SharedInfrastructure.start(configuration); }
			@Override public void stop(Set<String> targets) {
				var arguments = new java.util.ArrayList<String>();
				arguments.add("stop");
				arguments.addAll(targets);
				SharedInfrastructure.command(configuration, null, arguments.toArray(String[]::new));
			}
			@Override public void operator(String action) {
				if (action.equals("clean")) {
					SharedInfrastructure.command(configuration, null, "down", "-v", "--remove-orphans");
				} else { SharedInfrastructure.command(configuration, null, action); }
			}
		});
	}

	public Session join(Kind kind) {
		String token = UUID.randomUUID().toString();
		change(state -> {
			if (kind != Kind.AUTOMATED && state.participants.values().stream().anyMatch(p -> p.kind() != Kind.AUTOMATED)) {
				throw new IllegalStateException("A backend session is already active; local and manual share one HTTP port");
			}
			state.participants.put(token, new Participant(ProcessHandle.current().pid(), currentStart(), kind));
			return null;
		});
		return new Session(token);
	}

	public void pin() {
		change(state -> {
			Set<String> missing = new LinkedHashSet<>(SERVICES);
			missing.removeAll(services.running());
			state.automatic.addAll(missing);
			save(state); // Crash recovery must know ownership before starting services.
			try { services.start(); }
			catch (RuntimeException failure) { stopWhenIdle(state); throw failure; }
			state.pinned = true;
			state.automatic.clear();
			return null;
		});
	}

	public void operator(String action) {
		if (!Set.of("stop", "down", "clean").contains(action)) { throw new IllegalArgumentException("Unknown infrastructure action"); }
		change(state -> {
			if (!state.participants.isEmpty()) { throw new IllegalStateException("Infrastructure is in use; finish active backend/test sessions first"); }
			services.operator(action);
			state.automatic.clear();
			state.pinned = false;
			return null;
		});
	}

	private void start(String token) {
		change(state -> {
			if (!state.participants.containsKey(token)) { throw new IllegalStateException("Infrastructure session has already closed"); }
			Set<String> running = services.running();
			if (running.isEmpty()) { state.pinned = false; }
			Set<String> missing = new LinkedHashSet<>(SERVICES);
			missing.removeAll(running);
			if (!state.pinned) { state.automatic.addAll(missing); }
			save(state);
			services.start();
			return null;
		});
	}

	private void release(String token) {
		change(state -> {
			state.participants.remove(token);
			stopWhenIdle(state);
			return null;
		});
	}

	private void stopWhenIdle(State state) {
		if (state.participants.isEmpty() && !state.pinned && !state.automatic.isEmpty()) {
			// Validate persisted state before it can influence a Docker command.
			if (!SERVICES.containsAll(state.automatic)) { throw new IllegalStateException("Invalid infrastructure ownership state"); }
			services.stop(Set.copyOf(state.automatic));
			state.automatic.clear();
		}
	}

	private static String currentStart() {
		return ProcessHandle.current().info().startInstant().map(Object::toString).orElse("");
	}

	private static boolean alive(Participant participant) {
		if (participant.pid() <= 0) { return false; }
		return ProcessHandle.of(participant.pid()).filter(ProcessHandle::isAlive).filter(handle ->
				participant.startedAt().isEmpty() || handle.info().startInstant().map(Object::toString)
						.map(participant.startedAt()::equals).orElse(false)).isPresent();
	}

	private interface Change<T> { T apply(State state); }

	private <T> T change(Change<T> action) {
		synchronized (JVM_GUARD) {
			try {
				Files.createDirectories(directory);
				try (var channel = FileChannel.open(directory.resolve("registry.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
						var lock = channel.lock()) {
					Path file = directory.resolve("state.json");
					State state = Files.exists(file) ? JSON.readValue(Files.readString(file), State.class) : new State();
					state.participants.values().removeIf(participant -> !alive(participant));
					try { return action.apply(state); }
					finally { save(state); }
				}
			} catch (IOException failure) { throw new IllegalStateException("Could not coordinate infrastructure sessions", failure); }
		}
	}

	private void save(State state) {
		try {
			Path temporary = Files.createTempFile(directory, "state-", ".tmp");
			try {
				Files.writeString(temporary, JSON.writeValueAsString(state));
				try { Files.move(temporary, directory.resolve("state.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
				catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
					Files.move(temporary, directory.resolve("state.json"), StandardCopyOption.REPLACE_EXISTING);
				}
			} finally { Files.deleteIfExists(temporary); }
		} catch (IOException failure) { throw new IllegalStateException("Could not persist infrastructure ownership", failure); }
	}

	public final class Session implements AutoCloseable {
		private final String token;
		private boolean closed;
		private Session(String token) { this.token = token; }
		public void start() {
			try { InfrastructureSessions.this.start(token); }
			catch (RuntimeException failure) {
				try { close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
				throw failure;
			}
		}
		@Override public synchronized void close() {
			if (!closed) { closed = true; release(token); }
		}
	}
}
