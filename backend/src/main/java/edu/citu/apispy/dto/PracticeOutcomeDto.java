package edu.citu.apispy.dto;

/** Recorded ungraded practice attempt (never awards TVP or first-attempt evidence). */
public record PracticeOutcomeDto(
		String conceptTag,
		String challengeId,
		String roomMissionId,
		boolean correct,
		String recordedAt
) {
}
