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

@WebMvcTest(controllers = RequestQuotaMultiEndpointSecurityTest.TestRoutes.class, properties = {
		"AUTH0_ISSUER_URI=https://example.test/",
		"AUTH0_AUDIENCE=https://api.example.test",
		"moodly.rate-limit.enabled=true",
		"moodly.rate-limit.requests=5",
		"moodly.rate-limit.window=1h"
})
@Import({SecurityConfiguration.class, RequestQuotaMultiEndpointSecurityTest.TestRoutes.class})
@ActiveProfiles("prod")
class RequestQuotaMultiEndpointSecurityTest {
	@Autowired
	private MockMvc mvc;
	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void alternatingHabitsAndEntriesRequestsShareQuotaAndThrottleOnSixthCall() throws Exception {
		JwtRequestPostProcessor user = jwt().jwt(token -> token.subject("auth0|user_alternating"));

		// 5 GET requests alternating between /habits and /entries return 200 OK
		mvc.perform(get("/habits").with(user)).andExpect(status().isOk());
		mvc.perform(get("/entries").with(user)).andExpect(status().isOk());
		mvc.perform(get("/habits").with(user)).andExpect(status().isOk());
		mvc.perform(get("/entries").with(user)).andExpect(status().isOk());
		mvc.perform(get("/habits").with(user)).andExpect(status().isOk());

		// 6th request reaches limit and returns 429 Too Many Requests
		mvc.perform(get("/entries").with(user))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"));
	}

	@RestController
	static class TestRoutes {
		@GetMapping("/habits")
		String habits() {
			return "habits";
		}

		@GetMapping("/entries")
		String entries() {
			return "entries";
		}
	}
}
