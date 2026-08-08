package com.tlavu.moodly.modules.auth.infrastructure;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CloudinaryHttpAssetClient implements CloudinaryAssetClient {
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final ObjectMapper objectMapper;
	private final String cloudName;
	private final String authorization;
	private final String apiKey;
	private final String apiSecret;

	public CloudinaryHttpAssetClient(ObjectMapper objectMapper,
			@Value("${moodly.cloudinary.cloud-name}") String cloudName,
			@Value("${moodly.cloudinary.api-key}") String apiKey,
			@Value("${moodly.cloudinary.api-secret}") String apiSecret) {
		this.objectMapper = objectMapper;
		this.cloudName = cloudName;
		this.apiKey = apiKey; this.apiSecret = apiSecret;
		this.authorization = "Basic " + Base64.getEncoder().encodeToString((apiKey + ":" + apiSecret).getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public ConfirmedAsset findImage(String publicId) {
		var request = HttpRequest.newBuilder(URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/resources/image/upload/" + publicId))
				.header("Authorization", authorization).GET().build();
		try {
			var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) throw new IllegalArgumentException("Cloudinary could not confirm the uploaded avatar.");
			var asset = objectMapper.readTree(response.body());
			var format = asset.path("format").asString();
			var contentType = "jpg".equalsIgnoreCase(format) ? "image/jpeg" : "image/" + format.toLowerCase();
			return new ConfirmedAsset(asset.path("public_id").asString(), asset.path("version").asLong(), contentType, asset.path("bytes").asLong());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Cloudinary verification was interrupted.", exception);
		} catch (Exception exception) {
			throw new IllegalStateException("Cloudinary verification failed.", exception);
		}
	}

	@Override
	public void deleteImage(String publicId) {
		// Deletion is intentionally performed only after the replacement has been persisted.
		var timestamp = Instant.now().getEpochSecond();
		var signature = sha1("public_id=" + publicId + "&timestamp=" + timestamp + apiSecret);
		var body = "public_id=" + encode(publicId) + "&timestamp=" + timestamp + "&api_key=" + encode(apiKey) + "&signature=" + encode(signature);
		var request = HttpRequest.newBuilder(URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/image/destroy"))
				.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build();
		try { var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding()); if (response.statusCode() >= 300) throw new IllegalStateException("Cloudinary deletion failed."); }
		catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("Cloudinary deletion was interrupted.", exception); }
		catch (Exception exception) { throw new IllegalStateException("Cloudinary deletion failed.", exception); }
	}
	private static String sha1(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }
	private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
