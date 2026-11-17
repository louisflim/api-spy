package edu.citu.apispy.auth;

import edu.citu.apispy.config.AuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * Sliding-window limiter for authentication routes (max 5 attempts / 15 minutes by default).
 */
@Component
public class AuthRateLimiter {

	private final SlidingWindowRateLimiter limiter;

	public AuthRateLimiter(AuthProperties properties) {
		int maxAttempts = properties.getAuth().getMaxAttempts();
		int windowMinutes = Math.max(1, properties.getAuth().getWindowMinutes());
		this.limiter = new SlidingWindowRateLimiter(maxAttempts, Duration.ofMinutes(windowMinutes));
	}

	public void check(String key) {
		if (!limiter.tryAcquire(key)) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Try again later.");
		}
	}

	public boolean tryAcquire(String key) {
		return limiter.tryAcquire(key);
	}
}
