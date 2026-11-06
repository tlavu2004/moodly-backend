package com.tlavu.moodly.modules.auth.security.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = RequestQuotaWindowExpirySecurityTest.TestRoutes.class, properties = {
		"AUTH0_ISSUER_URI=https://example.test/",
		"AUTH0_AUDIENCE=https://api.example.test",
		"moodly.rate-limit.enabled=true",
		"moodly.rate-limit.requests=1",
		"moodly.rate-limit.window=100ms"
})
@Import({SecurityConfiguration.class, RequestQuotaWindowExpirySecurityTest.TestRoutes.class})
@ActiveProfiles("prod")
class RequestQuotaWindowExpirySecurityTest {
	@Autowired
	private MockMvc mvc;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void userBlockedByRateLimitIsAdmittedAfterWindowDurationExpires() throws Exception {
		JwtRequestPostProcessor user = jwt().jwt(token -> token.subject("auth0|user_expiry"));

		// 1st request -> 200 OK
		mvc.perform(get("/quota-expiry-test").with(user)).andExpect(status().isOk());

		// 2nd request -> 429 Too Many Requests with Retry-After header
		mvc.perform(get("/quota-expiry-test").with(user))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));

		// Wait for window to expire (> 100ms)
		Thread.sleep(150);

		// Subsequent request after waiting the required duration is admitted -> 200 OK
		mvc.perform(get("/quota-expiry-test").with(user)).andExpect(status().isOk());
	}

	@RestController
	static class TestRoutes {
		@GetMapping("/quota-expiry-test")
		String ok() {
			return "ok";
		}
	}
}
