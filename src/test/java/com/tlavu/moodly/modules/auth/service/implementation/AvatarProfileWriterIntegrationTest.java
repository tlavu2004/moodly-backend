package com.tlavu.moodly.modules.auth.service.implementation;

import static org.assertj.core.api.Assertions.assertThat;

import com.tlavu.moodly.modules.auth.model.UserProfile;
import com.tlavu.moodly.modules.auth.repository.UserProfileRepository;
import com.tlavu.moodly.support.MongoTestConfiguration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(MongoTestConfiguration.class)
class AvatarProfileWriterIntegrationTest {
	private static final String SUBJECT = "__test_avatar_concurrency__";
	@Autowired private UserProfileRepository profiles;
	@Autowired private AvatarProfileWriter writer;

	@AfterEach
	void cleanUp() {
		profiles.findByAuth0Subject(SUBJECT).ifPresent(profiles::delete);
	}

	@Test
	void onlyOneStaleAvatarReplacementCanSucceed() throws Exception {
		UserProfile initial = new UserProfile(SUBJECT, "user@example.test", Instant.now());
		initial.replaceAvatar("old", 1, "image/png", 100, Instant.now());
		profiles.save(initial);
		UserProfile first = profiles.findByAuth0Subject(SUBJECT).orElseThrow();
		UserProfile second = profiles.findByAuth0Subject(SUBJECT).orElseThrow();
		first.replaceAvatar("new-one", 2, "image/png", 100, Instant.now());
		second.replaceAvatar("new-two", 2, "image/png", 100, Instant.now());

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			CountDownLatch start = new CountDownLatch(1);
			Future<Boolean> one = executor.submit(() -> { start.await(); return writer.replaceIfCurrent(first, "old"); });
			Future<Boolean> two = executor.submit(() -> { start.await(); return writer.replaceIfCurrent(second, "old"); });
			start.countDown();
			assertThat(one.get() ^ two.get()).isTrue();
		}
		assertThat(profiles.findByAuth0Subject(SUBJECT).orElseThrow().getAvatarPublicId())
				.isIn("new-one", "new-two");
	}
}
