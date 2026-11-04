package com.tlavu.moodly.modules.auth.security.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import com.tlavu.moodly.shared.error.code.global.GlobalErrorCode;

final class RequestQuotaFilter extends OncePerRequestFilter {
	private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();
	private static final RequestMatcher EXEMPT = new OrRequestMatcher(
			PATHS.matcher(HttpMethod.OPTIONS, "/**"),
			PATHS.matcher("/actuator/health"),
			PATHS.matcher("/internal/**"),
			PATHS.matcher("/swagger-ui.html"),
			PATHS.matcher("/swagger-ui/**"),
			PATHS.matcher("/v3/api-docs/**")
	);

	private final RequestQuota quota;
	private final ObjectMapper objectMapper;

	RequestQuotaFilter(RequestQuota quota, ObjectMapper objectMapper) {
		this.quota = quota;
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return EXEMPT.matches(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication
				&& authentication.isAuthenticated()) {
			String subject = authentication.getToken().getSubject();
			if (subject != null && !subject.isBlank()) {
				long retryAfter = quota.retryAfter(subject);
				if (retryAfter > 0) {
					response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
					SecurityConfiguration.writeError(response, objectMapper, GlobalErrorCode.RATE_LIMIT_EXCEEDED,
							request.getRequestURI());
					return;
				}
			}
		}
		chain.doFilter(request, response);
	}
}
