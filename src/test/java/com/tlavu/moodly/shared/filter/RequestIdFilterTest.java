package com.tlavu.moodly.shared.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

	@Test
	void propagatesSafeRequestIdsAndReplacesUnsafeValues() throws Exception {
		RequestIdFilter filter = new RequestIdFilter();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(RequestIdFilter.HEADER, "client-request-42");
		MockHttpServletResponse response = new MockHttpServletResponse();
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
