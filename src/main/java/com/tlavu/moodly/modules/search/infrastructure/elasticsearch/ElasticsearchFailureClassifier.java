package com.tlavu.moodly.modules.search.infrastructure.elasticsearch;


public final class ElasticsearchFailureClassifier {

	private ElasticsearchFailureClassifier() {}

	public static boolean isUnavailable(ElasticsearchException exception) {
		int status = exception.status();

		return status == 404 ||
				status == 408 ||
				status == 429 ||
				status >= 500;
	}
}
