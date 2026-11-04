package com.tlavu.moodly.support;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/** Applies to infrastructure integration tests, including direct Maven and IDE runs. */
public class ComposeTestContextCustomizerFactory implements ContextCustomizerFactory {

	@Override
	public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> attributes) {
		Import imports = AnnotatedElementUtils.findMergedAnnotation(testClass, Import.class);
		return imports != null && Arrays.asList(imports.value()).contains(MongoTestConfiguration.class)
				? new ComposeCustomizer() : null;
	}

	private record ComposeCustomizer() implements ContextCustomizer {
		@Override
		public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration configuration) {
			ComposeTestEnvironment environment = ComposeTestEnvironment.get();
			environment.prepare(context);
			TestPropertyValues.of(
					"spring.mongodb.uri=" + environment.mongoUri(environment.database()),
					"spring.elasticsearch.uris=" + environment.searchUri(),
					"moodly.search.index.name=" + environment.index()
			).applyTo(context);
		}
	}
}
