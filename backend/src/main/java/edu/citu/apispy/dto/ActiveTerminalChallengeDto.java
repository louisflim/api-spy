package edu.citu.apispy.dto;

/**
 * Server-selected challenge currently active in the mission terminal sequence.
 * Docs only — never includes expected method/path answer keys.
 */
public record ActiveTerminalChallengeDto(
		String challengeId,
		String variantId,
		String deviceId,
		String documentation,
		boolean optionalVerification,
		boolean sequenceComplete
) {
}
