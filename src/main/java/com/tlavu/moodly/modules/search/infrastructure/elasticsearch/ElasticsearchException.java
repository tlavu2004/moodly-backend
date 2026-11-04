package com.tlavu.moodly.modules.search.infrastructure.elasticsearch;

public class ElasticsearchException extends RuntimeException {
	private final int status;

	public ElasticsearchException(int status) {
		super("Elasticsearch returned HTTP " + status);
		this.status = status;
	}

	public int status() {
		return status;
	}
}
