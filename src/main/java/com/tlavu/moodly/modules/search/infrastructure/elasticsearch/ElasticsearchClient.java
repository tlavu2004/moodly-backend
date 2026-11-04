package com.tlavu.moodly.modules.search.infrastructure.elasticsearch;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** JSON REST operations supported by Elasticsearch OSS 7.10.2. */
@Component
public class ElasticsearchClient implements AutoCloseable {
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(3))
			.version(HttpClient.Version.HTTP_1_1)
			.build();
	private final ObjectMapper json;
	private final String base;
	private final String authorization;

	public ElasticsearchClient(ObjectMapper json,
			@Value("${spring.elasticsearch.uris}") String endpoint,
			@Value("${spring.elasticsearch.username:}") String username,
			@Value("${spring.elasticsearch.password:}") String password) {
		this.json = json;
		URI uri = URI.create(endpoint);

		if (
				uri.getHost() == null ||
				uri.getUserInfo() != null ||
				uri.getQuery() != null ||
				uri.getFragment() != null ||
				!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
		) {
			throw new IllegalArgumentException(
					"Elasticsearch requires one HTTP(S) endpoint without embedded credentials"
			);
		}
		if (
				username.isEmpty() != password.isEmpty() ||
				(!username.isEmpty() && !"https".equals(uri.getScheme()))
		) {
			throw new IllegalArgumentException(
					"Elasticsearch Basic authentication requires HTTPS and both credentials"
			);
		}

		base = endpoint.replaceAll("/+$", "");
		authorization = username.isEmpty()
				? null
				: "Basic " + Base64.getEncoder()
						.encodeToString(
							(username + ":" + password).getBytes(StandardCharsets.UTF_8)
						);
	}

	public JsonNode request(
			String method,
			String path,
			Object payload
	) throws IOException {
		return request(method, path, payload, false);
	}

	private JsonNode request(
			String method,
			String path,
			Object payload,
			boolean allowMissing
	) throws IOException {
		HttpRequest.BodyPublisher body = payload == null
				? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(
						payload instanceof String raw
								? raw
								: json.writeValueAsString(payload)
				);
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
				.header("Accept", "application/json").method(method, body);

		if (payload != null) {
			builder.header("Content-Type", "application/json");
		}
		if (authorization != null) {
			builder.header("Authorization", authorization);
		}

		try {
			HttpResponse<String> response = http.send(
					builder.build(),
					HttpResponse.BodyHandlers.ofString()
			);
			int status = response.statusCode();

			if (status == 404 && allowMissing) {
				return response.body().isBlank()
						? null
						: json.readTree(response.body());
			}
			if (status < 200 || status >= 300) {
				throw new ElasticsearchException(status);
			}

			return response.body().isBlank()
					? json.createObjectNode()
					: json.readTree(response.body());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();

			throw new IOException(
					"Elasticsearch request interrupted",
					interrupted
			);
		}
	}

	public boolean exists(String index) throws IOException {
		return request(
				"HEAD",
				path(index),
				null,
				true
		) != null;
	}

	public void createIndex(String index, String mapping) throws IOException {
		request(
				"PUT",
				path(index),
				mapping
		);
	}

	public void deleteIndex(String index) throws IOException {
		request(
				"DELETE",
				path(index),
				null
		);
	}

	public void index(String index, String id, Object document) throws IOException {
		request(
				"PUT",
				documentPath(index, id),
				document
		);
	}

	public void delete(String index, String id) throws IOException {
		JsonNode result = request(
				"DELETE",
				documentPath(index, id),
				null,
				true
		);

		if (result == null || result.has("error")) {
			throw new ElasticsearchException(404);
		}
	}

	public <T> T get(String index, String id, Class<T> type) throws IOException {
		JsonNode result = request(
				"GET",
				documentPath(index, id),
				null,
				true
		);

		if (result == null || result.has("error")) {
			throw new ElasticsearchException(404);
		}

		return result.path("found").asBoolean()
				? json.treeToValue(result.get("_source"), type)
				: null;
	}

	public JsonNode search(String index, Map<String, Object> query) throws IOException {
		return request(
				"POST",
				path(index) + "/_search",
				query
		);
	}

	public void refresh(String index) throws IOException {
		request(
				"POST",
				path(index) + "/_refresh",
				null
		);
	}

	public void deleteByUser(String index, String userId) throws IOException {
		String endpoint = path(index) + "/_delete_by_query?refresh=true";
		Map<String, Object> query = Map.of("query", Map.of("term", Map.of("userId", userId)));
		JsonNode result;

		try {
			result = request("POST", endpoint, query);
		} catch (ElasticsearchException conflict) {
			if (conflict.status() != 409) {
				throw conflict;
			}

			// CDC can update a document after delete-by-query takes its snapshot.
			refresh(index);
			result = request("POST", endpoint, query);
		}

		if (
				result.path("timed_out").asBoolean() ||
				!result.path("failures").isEmpty()
		) {
			throw new IOException("Elasticsearch delete-by-query did not complete");
		}
	}

	static String path(String index) {
		if (!index.matches("[a-z0-9][a-z0-9_.-]*")) {
			throw new IllegalArgumentException("Invalid Elasticsearch index name");
		}

		return "/" + index;
	}

	private static String documentPath(String index, String id) {
		return path(index) + "/_doc/" + UriUtils.encodePathSegment(id, StandardCharsets.UTF_8);
	}

	@Override
	public void close() {
		http.close();
	}
}
