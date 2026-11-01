package com.tlavu.moodly.modules.e2e.service.implementation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class E2eUserResetServiceConditionTest {

	@Test
	void doesNotRegisterWhenResetIsDisabled() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.getEnvironment().setActiveProfiles("local");
			context.register(E2eUserResetService.class);
			context.refresh();

			assertThat(context.getBeansOfType(E2eUserResetService.class)).isEmpty();
		}
	}

	@Test
	void doesNotRegisterInProductionEvenWhenResetIsEnabled() {
		for (String profile : List.of("prod", "production")) {
			try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
				context.getEnvironment().setActiveProfiles(profile);
				context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
						"e2e-test", Map.of("moodly.e2e.reset.enabled", "true")));
				context.register(E2eUserResetService.class);
				context.refresh();

				assertThat(context.getBeansOfType(E2eUserResetService.class)).isEmpty();
			}
		}
	}
}
