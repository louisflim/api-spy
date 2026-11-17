package edu.citu.apispy.controller;

import edu.citu.apispy.config.AuthProperties;
import edu.citu.apispy.security.ApiRateLimitFilter;
import edu.citu.apispy.auth.ApiRateLimiter;
import edu.citu.apispy.auth.AuthRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiRateLimitFilterTest {

	private ApiRateLimitFilter filter;

	@BeforeEach
	void setUp() {
		AuthProperties properties = new AuthProperties();
		properties.getAuth().setMaxAttempts(5);
		properties.getAuth().setWindowMinutes(15);
		properties.getRateLimit().setMaxRequestsPerMinute(3);
		filter = new ApiRateLimitFilter(new AuthRateLimiter(properties), new ApiRateLimiter(properties));
	}

	@Test
	void blocksSixthAuthLoginAttempt() throws Exception {
		for (int i = 0; i < 5; i++) {
			MockHttpServletResponse response = new MockHttpServletResponse();
			filter.doFilter(loginRequest(), response, new MockFilterChain());
			assertEquals(200, response.getStatus(), "attempt " + (i + 1));
		}

		MockHttpServletResponse blocked = new MockHttpServletResponse();
		filter.doFilter(loginRequest(), blocked, new MockFilterChain());
		assertEquals(429, blocked.getStatus());
	}

	@Test
	void blocksGeneralApiAfterBudgetExhausted() throws Exception {
		for (int i = 0; i < 3; i++) {
			MockHttpServletResponse response = new MockHttpServletResponse();
			filter.doFilter(healthRequest(), response, new MockFilterChain());
			assertEquals(200, response.getStatus());
		}

		MockHttpServletResponse blocked = new MockHttpServletResponse();
		filter.doFilter(healthRequest(), blocked, new MockFilterChain());
		assertEquals(429, blocked.getStatus());
	}

	private static MockHttpServletRequest loginRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
		request.setRemoteAddr("10.0.0.1");
		return request;
	}

	private static MockHttpServletRequest healthRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
		request.setRemoteAddr("10.0.0.2");
		return request;
	}
}
