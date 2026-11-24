import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

class SmokeOidc {
	private static final String MODULUS = "zDLMq8wxqOpXzCvfGXS7HbzNubtxCyuhDlqOgGoY0EUgg3k7s2IWBk_U90RxFz_jehk8BTIqFWinheUo7Mp4iAi4O6jUJsHw42cG2o_0yl8J7pGnr8B4aByk74VzAWdYtTLEJBfANDgawBjz2GZGVdKxfTovH17E6qqHwRTOOroTQbARAc2zK5ewrQjYtCrtPBo9ArrYMWyXJN84SQhPwkdSukw7jn-razNA0ZE5ndlF-6WQPScvPc0k-fXUtUGM_8Tt67TzM8ycQ4EFdrwJcRGifoweoOryFOiSShHOd5ohNRDRRpRN2RKLm8bTIcReSeLhj74QODTweig6828Ktw";

	public static void main(String[] args) throws Exception {
		if (args.length > 0 && args[0].equals("check")) {
			try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
				String health = request(client, "GET", "/actuator/health", Set.of(200)).replaceAll("\\s+", "");
				if (!health.matches(".*[,{]\"status\":\"UP\"[,}].*")
						|| health.matches(".*[,{]\"(?:components|details)\":.*")) {
					throw new IllegalStateException("Unexpected public health payload: " + health);
				}
				request(client, "GET", "/habits", Set.of(401));
				request(client, "POST", "/internal/e2e/reset", Set.of(401, 404));
				request(client, "GET", "/v3/api-docs", Set.of(401, 404));
			}
			System.out.println("PASS: public health, protected API, reset and docs unavailable");
			return;
		}
		var server = HttpServer.create(new InetSocketAddress(8000), 0);
		server.createContext("/", exchange -> {
			try (exchange) {
				String body = exchange.getRequestURI().getPath().equals("/jwks")
						? "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"smoke\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\"" + MODULUS + "\",\"e\":\"AQAB\"}]}"
						: "{\"issuer\":\"http://oidc:8000/\",\"jwks_uri\":\"http://oidc:8000/jwks\",\"id_token_signing_alg_values_supported\":[\"RS256\"]}";
				byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, bytes.length);
				exchange.getResponseBody().write(bytes);
			}
		});
		server.start();
	}

	private static String request(HttpClient client, String method, String path, Set<Integer> expected) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://backend:8080" + path)).timeout(Duration.ofSeconds(15))
				.method(method, HttpRequest.BodyPublishers.noBody()).build();
		var response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (!expected.contains(response.statusCode())) {
			throw new IllegalStateException(path + " returned HTTP " + response.statusCode());
		}
		return response.body();
	}
}
