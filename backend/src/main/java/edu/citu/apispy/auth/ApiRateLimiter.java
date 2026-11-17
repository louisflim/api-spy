package edu.citu.apispy.auth;

import edu.citu.apispy.config.AuthProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Sliding-window limiter for general /api/** traffic.
 */
@Component
public class ApiRateLimiter {

	private final SlidingWindowRateLimiter limiter;

	public ApiRateLimiter(AuthProperties properties) {
		int maxPerMinute = properties.getRateLimit().getMaxRequestsPerMinute();
		this.limiter = new SlidingWindowRateLimiter(maxPerMinute, Duration.ofMinutes(1));
	}

	public boolean tryAcquire(String key) {
		return limiter.tryAcquire(key);
	}
}
