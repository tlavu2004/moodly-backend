package com.tlavu.moodly.modules.search.infrastructure.elasticsearch;

/** Failure to communicate with Elasticsearch or complete a transport operation. */
public final class ElasticsearchTransportException extends RuntimeException {
	public ElasticsearchTransportException(String message) {
		super(message);
	}

	public ElasticsearchTransportException(String message, Throwable cause) {
		super(message, cause);
	}
}
