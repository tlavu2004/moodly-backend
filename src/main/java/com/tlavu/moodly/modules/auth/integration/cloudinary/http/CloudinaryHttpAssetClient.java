package com.tlavu.moodly.modules.auth.integration.cloudinary.http;

import com.tlavu.moodly.modules.auth.exception.AssetSignatureException;

import com.tlavu.moodly.modules.auth.exception.AvatarException;
import com.tlavu.moodly.modules.auth.exception.AvatarAssetDeletionException;
import com.tlavu.moodly.modules.auth.exception.AvatarServiceUnavailableException;
import com.tlavu.moodly.modules.auth.integration.cloudinary.model.ConfirmedAsset;
import com.tlavu.moodly.modules.auth.integration.cloudinary.port.CloudinaryAssetClient;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

@Component
public class CloudinaryHttpAssetClient implements CloudinaryAssetClient {
	private final HttpClient httpClient;
	private final Duration requestTimeout;
	private final ObjectMapper objectMapper;
	private final String cloudName;
	private final String authorization;
	private final String apiKey;
	private final String apiSecret;

	@Autowired
	public CloudinaryHttpAssetClient(
			ObjectMapper objectMapper,
			@Value("${moodly.cloudinary.cloud-name}") String cloudName,
			@Value("${moodly.cloudinary.api-key}") String apiKey,
			@Value("${moodly.cloudinary.api-secret}") String apiSecret,
			@Value("${moodly.cloudinary.connect-timeout:2s}") Duration connectTimeout,
			@Value("${moodly.cloudinary.request-timeout:5s}") Duration requestTimeout
	) {
		this(
				objectMapper,
				cloudName,
				apiKey,
				apiSecret,
				requestTimeout,
				HttpClient.newBuilder()
						.connectTimeout(connectTimeout)
						.build()
		);
	}

	CloudinaryHttpAssetClient(
			ObjectMapper objectMapper,
			String cloudName,
			String apiKey,
			String apiSecret,
			Duration requestTimeout,
			HttpClient httpClient
	) {
		this.objectMapper = objectMapper;
		this.cloudName = cloudName;
		this.apiKey = apiKey;
		this.apiSecret = apiSecret;
		this.httpClient = httpClient;
		this.requestTimeout = requestTimeout;
		this.authorization = "Basic " +
				Base64.getEncoder()
						.encodeToString(
							(apiKey + ":" + apiSecret).getBytes(StandardCharsets.UTF_8)
						);
	}

	@Override
	public ConfirmedAsset findImage(String publicId) {
		URI uri = URI.create(
				"https://api.cloudinary.com/v1_1/" + cloudName +
				"/resources/image/upload/" + publicId
		);
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(requestTimeout)
				.header("Authorization", authorization)
				.GET()
				.build();

		try {
			HttpResponse<String> response = httpClient.send(
					request,
					HttpResponse.BodyHandlers.ofString()
			);

			if (response.statusCode() == 404) {
				throw AvatarException.uploadNotFound();
			}
			if (response.statusCode() != 200) {
				throw new AvatarServiceUnavailableException(response.statusCode());
			}

			JsonNode asset = objectMapper.readTree(response.body());
			String format = asset.path("format").asString();
			String contentType = "jpg".equalsIgnoreCase(format)
					? "image/jpeg"
					: "image/" + format.toLowerCase();

			return new ConfirmedAsset(
					asset.path("public_id").asString(),
					asset.path("version").asLong(),
					contentType,
					asset.path("bytes").asLong()
			);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();

			throw new AvatarServiceUnavailableException(exception);
		} catch (IOException | JacksonException exception) {
			throw new AvatarServiceUnavailableException(exception);
		}
	}

	@Override
	public void deleteImage(String publicId) {
		// Deletion is intentionally performed only after the replacement has been persisted.
		long timestamp = Instant.now().getEpochSecond();
		String signature = sha1(
					"public_id=" + publicId +
					"&timestamp=" + timestamp +
					apiSecret
		);
		String body = "public_id=" + encode(publicId) +
				"&timestamp=" + timestamp +
				"&api_key=" + encode(apiKey) +
				"&signature=" + encode(signature);
		URI uri = URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/image/destroy");
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(requestTimeout)
				.header(
						"Content-Type",
						"application/x-www-form-urlencoded"
				)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();

		try {
			HttpResponse<Void> response = httpClient.send(
					request,
					HttpResponse.BodyHandlers.discarding()
			);

			if (response.statusCode() >= 300) {
				throw new AvatarAssetDeletionException("Cloudinary deletion failed.", response.statusCode());
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();

			throw new AvatarAssetDeletionException(
					"Cloudinary deletion was interrupted.",
					exception
			);
		} catch (IOException exception) {
			throw new AvatarAssetDeletionException(
					"Cloudinary deletion failed.",
					exception
			);
		}
	}

	private static String sha1(String value) {
		try {
			return HexFormat.of().formatHex(
				MessageDigest.getInstance("SHA-1")
						.digest(value.getBytes(StandardCharsets.UTF_8))
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new AssetSignatureException(
					"SHA-1 is unavailable for asset signing",
					exception
			);
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
