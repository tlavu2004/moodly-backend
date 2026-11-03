package com.tlavu.moodly.support;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/** Cleanup on Maven/IDE test-session completion; JVM shutdown remains a fallback. */
public class ComposeTestSessionListener implements LauncherSessionListener {
	@Override
	public void launcherSessionClosed(LauncherSession session) {
		ComposeTestEnvironment.stop();
	}
}
