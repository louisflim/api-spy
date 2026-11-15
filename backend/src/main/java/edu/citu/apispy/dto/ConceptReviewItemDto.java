package edu.citu.apispy.dto;

/** Queued authored review scenario (not same-session mastery). */
public record ConceptReviewItemDto(
		String conceptTag,
		String sourceMissionId,
		String status,
		String reviewChallengeId
) {
}
