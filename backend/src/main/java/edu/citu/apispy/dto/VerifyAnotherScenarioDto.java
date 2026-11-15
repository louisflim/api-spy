package edu.citu.apispy.dto;

/**
 * Optional distinct authored verification challenge after mandatory unlock.
 * Graded — documentation only; method/path answers remain server-side.
 */
public record VerifyAnotherScenarioDto(
		String challengeId,
		String variantId,
		String deviceId,
		String documentation
) {
}
