package com.tlavu.moodly.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

	@Test
	void propagatesSafeRequestIdsAndReplacesUnsafeValues() throws Exception {
		var filter = new RequestIdFilter();
		var request = new MockHttpServletRequest();
		request.addHeader(RequestIdFilter.HEADER, "client-request-42");
		var response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-request-42");

		request = new MockHttpServletRequest();
		request.addHeader(RequestIdFilter.HEADER, "unsafe value\nAuthorization: secret");
		response = new MockHttpServletResponse();
		filter.doFilter(request, response, new MockFilterChain());
		assertThat(response.getHeader(RequestIdFilter.HEADER))
				.matches("[0-9a-f-]{36}")
				.doesNotContain("secret");
	}
}
