package com.tlavu.moodly.modules.auth.integration.cloudinary.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tlavu.moodly.modules.auth.exception.AvatarException;
import com.tlavu.moodly.modules.auth.exception.AvatarServiceUnavailableException;
import com.tlavu.moodly.modules.auth.integration.cloudinary.contract.ConfirmedAsset;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class CloudinaryHttpAssetClientTest {
	private static final String PUBLIC_ID = "moodly/users/test/avatar/image-1";
	private final HttpClient httpClient = mock(HttpClient.class);
	private final CloudinaryHttpAssetClient client = new CloudinaryHttpAssetClient(
			new ObjectMapper(), "test-cloud", "test-key", "test-secret", Duration.ofSeconds(5), httpClient
	);

	@Test
	void readsConfirmedImageAndSendsAuthenticatedRequest() throws Exception {
		HttpResponse<String> response = response(200,
				"{\"public_id\":\"" + PUBLIC_ID + "\",\"version\":7,\"format\":\"jpg\",\"bytes\":123}");
		doReturn(response).when(httpClient).send(any(HttpRequest.class), any());

		ConfirmedAsset asset = client.findImage(PUBLIC_ID);

		assertThat(asset).isEqualTo(new ConfirmedAsset(PUBLIC_ID, 7, "image/jpeg", 123));
		ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
		verify(httpClient).send(request.capture(), any());
		assertThat(request.getValue().uri()).isEqualTo(URI.create(
				"https://api.cloudinary.com/v1_1/test-cloud/resources/image/upload/" + PUBLIC_ID));
		assertThat(request.getValue().method()).isEqualTo("GET");
		assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(5));
		assertThat(request.getValue().headers().firstValue("Authorization"))
				.contains("Basic " + Base64.getEncoder().encodeToString("test-key:test-secret".getBytes()));
	}

	@Test
	void keepsNonJpegFormatAndRejectsMissingOrUnavailableImages() throws Exception {
		HttpResponse<String> png = response(200,
				"{\"public_id\":\"" + PUBLIC_ID + "\",\"version\":8,\"format\":\"PNG\",\"bytes\":456}");
		HttpResponse<String> missing = response(404, "");
		HttpResponse<String> unavailable = response(503, "");
		doReturn(png, missing, unavailable).when(httpClient).send(any(HttpRequest.class), any());

		assertThat(client.findImage(PUBLIC_ID).contentType()).isEqualTo("image/png");
		assertThatThrownBy(() -> client.findImage(PUBLIC_ID)).isInstanceOf(AvatarException.class);
		assertThatThrownBy(() -> client.findImage(PUBLIC_ID)).isInstanceOf(AvatarServiceUnavailableException.class);
	}

	@Test
	void wrapsTransportAndMalformedResponseFailures() throws Exception {
		HttpResponse<String> malformed = response(200, "not-json");
		doThrow(new IOException("network failure"))
				.doReturn(malformed)
				.when(httpClient).send(any(HttpRequest.class), any());

		assertThatThrownBy(() -> client.findImage(PUBLIC_ID))
				.isInstanceOf(AvatarServiceUnavailableException.class)
				.hasCauseInstanceOf(IOException.class);
		assertThatThrownBy(() -> client.findImage(PUBLIC_ID))
				.isInstanceOf(AvatarServiceUnavailableException.class);
	}

	@Test
	void preservesInterruptWhenImageLookupIsInterrupted() throws Exception {
		doThrow(new InterruptedException("shutdown")).when(httpClient).send(any(HttpRequest.class), any());

		try {
			assertThatThrownBy(() -> client.findImage(PUBLIC_ID))
					.isInstanceOf(AvatarServiceUnavailableException.class)
					.hasCauseInstanceOf(InterruptedException.class);
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void sendsSignedDeleteAndReportsRemoteFailure() throws Exception {
		doReturn(response(204, null), response(503, null))
				.when(httpClient).send(any(HttpRequest.class), any());

		client.deleteImage(PUBLIC_ID);
		ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
		verify(httpClient).send(request.capture(), any());
		assertThat(request.getValue().uri())
				.isEqualTo(URI.create("https://api.cloudinary.com/v1_1/test-cloud/image/destroy"));
		assertThat(request.getValue().method()).isEqualTo("POST");
		assertThat(request.getValue().headers().firstValue("Content-Type"))
				.contains("application/x-www-form-urlencoded");
		assertThatThrownBy(() -> client.deleteImage(PUBLIC_ID))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Cloudinary deletion failed.");
	}

	@Test
	void preservesInterruptWhenDeleteIsInterrupted() throws Exception {
		doThrow(new InterruptedException("shutdown")).when(httpClient).send(any(HttpRequest.class), any());

		try {
			assertThatThrownBy(() -> client.deleteImage(PUBLIC_ID))
					.isInstanceOf(IllegalStateException.class)
					.hasCauseInstanceOf(InterruptedException.class);
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		} finally {
			Thread.interrupted();
		}
	}

	@SuppressWarnings("unchecked")
	private <T> HttpResponse<T> response(int status, T body) {
		HttpResponse<T> response = mock(HttpResponse.class);
		when(response.statusCode()).thenReturn(status);
		when(response.body()).thenReturn(body);
		return response;
	}
}
