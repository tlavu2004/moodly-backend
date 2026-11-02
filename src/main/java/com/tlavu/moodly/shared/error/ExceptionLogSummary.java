package com.tlavu.moodly.shared.error;

/** A bounded, single-line diagnostic that never includes exception messages. */
public final class ExceptionLogSummary {
	private static final int MAX_CAUSES = 4;
	private static final String APPLICATION_PACKAGE = "com.tlavu.moodly.";

	private ExceptionLogSummary() {
	}

	public static String of(Throwable failure) {
		StringBuilder summary = new StringBuilder();
		Throwable current = failure;
		int causes = 0;
		while (current != null && causes < MAX_CAUSES) {
			if (causes > 0) {
				summary.append(" <- ");
			}
			summary.append(current.getClass().getSimpleName());
			StackTraceElement location = firstApplicationFrame(current);
			if (location != null) {
				summary.append(" at ").append(location.getClassName())
						.append('.').append(location.getMethodName())
						.append(':').append(location.getLineNumber());
			}
			current = current.getCause();
			causes++;
		}
		if (current != null) {
			summary.append(" <- ...");
		}
		return summary.toString();
	}

	private static StackTraceElement firstApplicationFrame(Throwable failure) {
		StackTraceElement[] frames = failure.getStackTrace();
		if (frames == null) {
			return null;
		}
		for (StackTraceElement frame : frames) {
			if (frame.getClassName().startsWith(APPLICATION_PACKAGE)) {
				return frame;
			}
		}
		return null;
	}
}
