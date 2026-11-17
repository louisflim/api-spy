package edu.citu.apispy.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestSizeLimitFilterTest {

	@Test
	void rejectsContentLengthOverBudget() throws Exception {
		RequestSizeLimitFilter filter = new RequestSizeLimitFilter(64);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
		request.setContent(new byte[80]);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(413, response.getStatus());
	}

	@Test
	void allowsSmallApiBodies() throws Exception {
		RequestSizeLimitFilter filter = new RequestSizeLimitFilter(64);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
		request.setContent("{\"a\":1}".getBytes());
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(200, response.getStatus());
	}
}
