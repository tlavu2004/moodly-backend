package com.tlavu.moodly.modules.search.monitoring;

import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchClient;
import com.tlavu.moodly.modules.search.infrastructure.elasticsearch.ElasticsearchException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component("elasticsearch")
@RequiredArgsConstructor
public class ElasticsearchHealthIndicator implements HealthIndicator {
	private final ElasticsearchClient client;

	@Override
	public Health health() {
		try {
			JsonNode response = client.request(
					"GET",
					"/_cluster/health",
					null
			);
			String status = response.path("status").asString();

			return (
					"green".equals(status) ||
					"yellow".equals(status)
							? Health.up()
							: Health.down()
			).withDetail("status", status).build();
		} catch (IOException | ElasticsearchException failure) {
			return Health.down()
					.withDetail(
							"error",
							failure.getClass().getSimpleName()
					).build();
		}
	}
}
