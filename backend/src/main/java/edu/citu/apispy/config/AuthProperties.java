package edu.citu.apispy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apispy")
public class AuthProperties {

	private final Auth auth = new Auth();
	private final RateLimit rateLimit = new RateLimit();
	private final Mail mail = new Mail();
	private final Cors cors = new Cors();

	public Auth getAuth() {
		return auth;
	}

	public RateLimit getRateLimit() {
		return rateLimit;
	}

	public Mail getMail() {
		return mail;
	}

	public Cors getCors() {
		return cors;
	}

	public static class Auth {
		/** Max attempts on auth routes (login/register/password-reset) per window. */
		private int maxAttempts = 5;
		/** Sliding window length for auth route rate limiting, in minutes. */
		private int windowMinutes = 15;

		public int getMaxAttempts() {
			return maxAttempts;
		}

		public void setMaxAttempts(int maxAttempts) {
			this.maxAttempts = maxAttempts;
		}

		public int getWindowMinutes() {
			return windowMinutes;
		}

		public void setWindowMinutes(int windowMinutes) {
			this.windowMinutes = windowMinutes;
		}
	}

	public static class RateLimit {
		/** Max requests per client for general /api/** traffic per minute. */
		private int maxRequestsPerMinute = 120;

		public int getMaxRequestsPerMinute() {
			return maxRequestsPerMinute;
		}

		public void setMaxRequestsPerMinute(int maxRequestsPerMinute) {
			this.maxRequestsPerMinute = maxRequestsPerMinute;
		}
	}

	public static class Mail {
		private boolean enabled = false;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}

	public static class Cors {
		private String allowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";

		public String getAllowedOrigins() {
			return allowedOrigins;
		}

		public void setAllowedOrigins(String allowedOrigins) {
			this.allowedOrigins = allowedOrigins;
		}
	}
}
