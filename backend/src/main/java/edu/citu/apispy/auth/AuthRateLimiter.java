package edu.citu.apispy.auth;

import edu.citu.apispy.config.AuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory sliding-window limiter for authentication endpoints.
 */
@Component
public class AuthRateLimiter {

	private final int maxPerMinute;
	private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

	public AuthRateLimiter(AuthProperties properties) {
		this.maxPerMinute = Math.max(1, properties.getAuth().getMaxFailedAttemptsPerMinute());
	}

	public void check(String key) {
		long now = Instant.now().toEpochMilli();
		long windowStart = now - 60_000L;
		Deque<Long> queue = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
		synchronized (queue) {
			while (!queue.isEmpty() && queue.peekFirst() < windowStart) {
				queue.removeFirst();
			}
			if (queue.size() >= maxPerMinute) {
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Try again later.");
			}
			queue.addLast(now);
		}
	}
}
