package com.tlavu.moodly.modules.auth.security.config;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = RequestQuotaSecurityTest.TestRoutes.class, properties = {
		"AUTH0_ISSUER_URI=https://example.test/",
		"AUTH0_AUDIENCE=https://api.example.test",
		"moodly.rate-limit.enabled=true",
		"moodly.rate-limit.requests=1",
		"moodly.rate-limit.window=1h"
})
@Import({SecurityConfiguration.class, RequestQuotaSecurityTest.TestRoutes.class})
@ActiveProfiles("prod")
class RequestQuotaSecurityTest {
	@Autowired
	private MockMvc mvc;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void rateLimitsByAuthenticatedSubjectAndPreservesSecurityAndHealth() throws Exception {
		JwtRequestPostProcessor alice = jwt().jwt(token -> token.subject("alice"));
		JwtRequestPostProcessor bob = jwt().jwt(token -> token.subject("bob"));
		when(jwtDecoder.decode("invalid")).thenThrow(new BadJwtException("Invalid token"));

		mvc.perform(get("/quota-test").with(alice)).andExpect(status().isOk());
		mvc.perform(get("/quota-test").with(jwt().jwt(token -> token.subject("alice").tokenValue("renewed")))
						.header("X-Forwarded-For", "192.0.2.1"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
		mvc.perform(get("/quota-test").with(bob)).andExpect(status().isOk());
		mvc.perform(get("/quota-test")).andExpect(status().isUnauthorized());
		mvc.perform(get("/quota-test").header("Authorization", "Bearer invalid"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/internal/cdc/quota-test").with(alice)).andExpect(status().isForbidden());
		mvc.perform(get("/internal/cdc/quota-test").with(jwt().jwt(token -> token.subject("alice"))
						.authorities(() -> "SCOPE_moodly:admin")))
				.andExpect(status().isOk());
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mvc.perform(get("/actuator/health").with(alice)).andExpect(status().isOk());
	}

	@Test
	void isolatesQuotaIndependentlyBetweenDistinctAuthenticatedUsers() throws Exception {
		JwtRequestPostProcessor userA = jwt().jwt(token -> token.subject("auth0|user_alpha"));
		JwtRequestPostProcessor userB = jwt().jwt(token -> token.subject("auth0|user_beta"));

		// User A: 1st request -> 200 OK
		mvc.perform(get("/quota-test").with(userA)).andExpect(status().isOk());

		// User A: 2nd request -> 429 Too Many Requests (reaches rate limit)
		mvc.perform(get("/quota-test").with(userA))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));

		// User B: 1st request -> 200 OK (unaffected by User A's exhausted quota)
		mvc.perform(get("/quota-test").with(userB)).andExpect(status().isOk());

		// User B: 2nd request -> 429 Too Many Requests (User B exhausts their own quota)
		mvc.perform(get("/quota-test").with(userB))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
	}

	@RestController
	static class TestRoutes {
		@GetMapping({"/quota-test", "/actuator/health", "/internal/cdc/quota-test"})
		String ok() {
			return "ok";
		}
	}
}
