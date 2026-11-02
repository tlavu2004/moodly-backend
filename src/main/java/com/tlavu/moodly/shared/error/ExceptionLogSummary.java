package com.tlavu.moodly.shared.error;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.mongodb.MongoException;

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
			appendSafeStatus(summary, current);
			StackTraceElement location = firstApplicationFrame(current);
			if (location != null) {
				summary.append(" at ").append(location.getClassName())
						.append('.').append(location.getMethodName())
						.append(':').append(location.getLineNumber());
			}
			StackTraceElement origin = firstFrame(current);
			if (origin != null &&
					!origin.getClassName().startsWith(APPLICATION_PACKAGE)) {
				summary.append(" from ").append(origin.getClassName())
						.append('.').append(origin.getMethodName())
						.append(':').append(origin.getLineNumber());
			}
			current = current.getCause();
			causes++;
		}
		if (current != null) {
			summary.append(" <- ...");
		}
		return summary.toString();
	}

	private static void appendSafeStatus(StringBuilder summary, Throwable failure) {
		if (failure instanceof ElasticsearchException elasticsearch && elasticsearch.status() > 0) {
			summary.append("[httpStatus=").append(elasticsearch.status()).append(']');
		} else if (failure instanceof ProviderHttpStatus provider && provider.providerHttpStatus() > 0) {
			summary.append("[httpStatus=").append(provider.providerHttpStatus()).append(']');
		} else if (failure instanceof MongoException mongo && mongo.getCode() > 0) {
			summary.append("[mongoCode=").append(mongo.getCode()).append(']');
		}
	}

	private static StackTraceElement firstFrame(Throwable failure) {
		StackTraceElement[] frames = failure.getStackTrace();
		return frames == null || frames.length == 0 ? null : frames[0];
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
