package edu.citu.apispy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apispy")
public class AuthProperties {

	private final Auth auth = new Auth();
	private final Mail mail = new Mail();
	private final Cors cors = new Cors();

	public Auth getAuth() {
		return auth;
	}

	public Mail getMail() {
		return mail;
	}

	public Cors getCors() {
		return cors;
	}

	public static class Auth {
		private int maxFailedAttemptsPerMinute = 20;

		public int getMaxFailedAttemptsPerMinute() {
			return maxFailedAttemptsPerMinute;
		}

		public void setMaxFailedAttemptsPerMinute(int maxFailedAttemptsPerMinute) {
			this.maxFailedAttemptsPerMinute = maxFailedAttemptsPerMinute;
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
