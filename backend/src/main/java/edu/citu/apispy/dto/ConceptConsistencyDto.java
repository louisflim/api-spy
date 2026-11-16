package edu.citu.apispy.dto;

import java.util.List;

/**
 * Bounded consistent-improvement observation state for one concept.
 * Separate from TVP / RCA / SCR / HLCR. Not a claim of validated mastery.
 */
public record ConceptConsistencyDto(
		String conceptTag,
		String status,
		int consecutiveSuccesses,
		int requiredConsecutiveSuccesses,
		int scenariosUsed,
		int maxAdditionalScenarios,
		String sourceChallengeId,
		List<String> plannedChallengeIds
) {
}
