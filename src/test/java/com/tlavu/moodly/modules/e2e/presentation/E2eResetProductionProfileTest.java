package com.tlavu.moodly.modules.e2e.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.tlavu.moodly.modules.e2e.application.E2eUserResetService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class E2eResetProductionProfileTest {

	@Test
	void doesNotRegisterResetEndpointInProductionProfiles() {
		for (var profile : List.of("prod", "production")) {
			try (var context = new AnnotationConfigApplicationContext()) {
				context.getEnvironment().setActiveProfiles(profile);
				context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
						"e2e-test", Map.of("moodly.e2e.reset.enabled", "true")));
				context.registerBean(E2eUserResetService.class, () -> new E2eUserResetService(null, null, null, null, null, "synthetic|moodly-playwright-e2e"));
				context.register(E2eResetController.class);
				context.refresh();
				assertThat(context.getBeansOfType(E2eResetController.class)).isEmpty();
			}
		}
	}
}
