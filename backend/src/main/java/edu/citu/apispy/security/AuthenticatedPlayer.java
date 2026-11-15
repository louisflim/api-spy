package edu.citu.apispy.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Resolves the authenticated player from the Spring Security session context.
 * Controllers must use this instead of trusting browser-supplied user ids.
 */
public final class AuthenticatedPlayer {

	private AuthenticatedPlayer() {
	}

	public static AccountPrincipal requirePrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
		}
		return principal;
	}

	public static UUID requireUserId() {
		return requirePrincipal().getId();
	}

	public static String requireUserIdString() {
		return requireUserId().toString();
	}
}
