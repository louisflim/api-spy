package edu.citu.apispy.dto.auth;

import java.util.UUID;

/**
 * Public player identity. Never includes password hashes.
 */
public record AuthUserResponseDto(
		UUID userId,
		String username,
		String email,
		/** PLAYER or ADMINISTRATOR. */
		String role
) {
}
