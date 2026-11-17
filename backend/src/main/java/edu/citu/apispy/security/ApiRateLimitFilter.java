package edu.citu.apispy.security;

import edu.citu.apispy.auth.ApiRateLimiter;
import edu.citu.apispy.auth.AuthRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Applies rate limits to every /api/** request.
 * Auth routes (login/register/password-reset) use the stricter auth window (default 5 / 15 min);
 * all other API routes use the general per-minute budget.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRateLimitFilter extends OncePerRequestFilter {

	private static final Set<String> AUTH_ROUTES = Set.of(
			"/api/auth/register",
			"/api/auth/login",
			"/api/auth/password-reset/request",
			"/api/auth/password-reset/confirm");

	private final AuthRateLimiter authRateLimiter;
	private final ApiRateLimiter apiRateLimiter;

	public ApiRateLimitFilter(AuthRateLimiter authRateLimiter, ApiRateLimiter apiRateLimiter) {
		this.authRateLimiter = authRateLimiter;
		this.apiRateLimiter = apiRateLimiter;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = normalizedPath(request);
		return !path.startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String path = normalizedPath(request);
		String client = clientKey(request);
		boolean allowed;

		if (isAuthRoute(request.getMethod(), path)) {
			String action = path.substring("/api/auth/".length()).replace('/', ':');
			allowed = authRateLimiter.tryAcquire(action + ":" + client);
		} else {
			allowed = apiRateLimiter.tryAcquire("api:" + client);
		}

		if (!allowed) {
			writeTooManyRequests(response);
			return;
		}

		filterChain.doFilter(request, response);
	}

	private static boolean isAuthRoute(String method, String path) {
		return HttpMethod.POST.matches(method) && AUTH_ROUTES.contains(path);
	}

	private static String normalizedPath(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String context = request.getContextPath();
		if (context != null && !context.isEmpty() && uri.startsWith(context)) {
			uri = uri.substring(context.length());
		}
		if (uri.length() > 1 && uri.endsWith("/")) {
			return uri.substring(0, uri.length() - 1);
		}
		return uri;
	}

	private static String clientKey(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
	}

	private static void writeTooManyRequests(HttpServletResponse response) throws IOException {
		response.setStatus(429);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(
				"{\"error\":\"Too many attempts. Try again later.\",\"message\":\"Too many attempts. Try again later.\"}");
	}
}
