package edu.citu.apispy.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory sliding-window rate limiter keyed by arbitrary strings (usually client IP).
 */
public class SlidingWindowRateLimiter {

	private final int maxHits;
	private final long windowMillis;
	private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

	public SlidingWindowRateLimiter(int maxHits, Duration window) {
		this.maxHits = Math.max(1, maxHits);
		this.windowMillis = Math.max(1L, window.toMillis());
	}

	/**
	 * @return {@code true} if the request is allowed and recorded; {@code false} if limited.
	 */
	public boolean tryAcquire(String key) {
		long now = Instant.now().toEpochMilli();
		long windowStart = now - windowMillis;
		Deque<Long> queue = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
		synchronized (queue) {
			while (!queue.isEmpty() && queue.peekFirst() < windowStart) {
				queue.removeFirst();
			}
			if (queue.size() >= maxHits) {
				return false;
			}
			queue.addLast(now);
			return true;
		}
	}
}
