package com.tlavu.moodly.modules.auth.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

class RequestQuotaFilterTest {
	private final RequestQuota quota = mock(RequestQuota.class);
	private final RequestQuotaFilter filter = new RequestQuotaFilter(quota, new ObjectMapper());

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "  ")
	void skipsQuotaForAuthenticatedJwtWithoutAUsableSubject(String subject) throws Exception {
		Jwt.Builder jwt = Jwt.withTokenValue("token").header("alg", "none").claim("iss", "https://issuer.test");
		if (subject != null) {
			jwt.subject(subject);
		}
		assertQuotaIsSkipped(new JwtAuthenticationToken(jwt.build(), List.of()));
	}

	@Test
	void skipsQuotaForUnauthenticatedJwt() throws Exception {
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").build();
		assertQuotaIsSkipped(new JwtAuthenticationToken(jwt));
	}

	private void assertQuotaIsSkipped(JwtAuthenticationToken authentication) throws Exception {
		SecurityContextHolder.getContext().setAuthentication(authentication);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/entries/today");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		verifyNoInteractions(quota);
		verify(chain).doFilter(request, response);
		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isNull();
	}
}
