package com.tlavu.moodly.support;

import java.util.Arrays;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/** A group is one integration test class, preserving replay/resume scenarios within it. */
public class ComposeTestGroupCleanupListener extends AbstractTestExecutionListener {
	@Override
	public int getOrder() {
		// afterTestClass executes in reverse order: reset after other listeners finish.
		return 0;
	}

	@Override
	public void afterTestClass(TestContext testContext) {
		Import imports = AnnotatedElementUtils.findMergedAnnotation(testContext.getTestClass(), Import.class);
		if (imports != null && Arrays.asList(imports.value()).contains(MongoTestConfiguration.class)) {
			try {
				ComposeTestEnvironment.finishCurrentGroup();
			} finally {
				testContext.markApplicationContextDirty(HierarchyMode.CURRENT_LEVEL);
			}
		}
	}
}
