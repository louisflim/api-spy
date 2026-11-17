package edu.citu.apispy.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowRateLimiterTest {

	@Test
	void allowsUpToMaxThenBlocks() {
		SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(5, Duration.ofMinutes(15));

		for (int i = 0; i < 5; i++) {
			assertTrue(limiter.tryAcquire("login:127.0.0.1"), "attempt " + (i + 1) + " should be allowed");
		}
		assertFalse(limiter.tryAcquire("login:127.0.0.1"), "6th attempt should be blocked");
	}

	@Test
	void keysAreIndependent() {
		SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1, Duration.ofMinutes(15));

		assertTrue(limiter.tryAcquire("login:1.1.1.1"));
		assertFalse(limiter.tryAcquire("login:1.1.1.1"));
		assertTrue(limiter.tryAcquire("login:2.2.2.2"));
	}
}
