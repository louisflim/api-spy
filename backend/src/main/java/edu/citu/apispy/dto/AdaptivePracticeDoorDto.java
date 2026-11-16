package edu.citu.apispy.dto;

import java.util.List;

/**
 * Adaptive practice door offered in the current room (ungraded).
 * Includes authored mini-lesson id and alternate practice variants for voluntary rounds.
 * Does not expose graded mission answer keys.
 */
public record AdaptivePracticeDoorDto(
		String conceptTag,
		String challengeId,
		String deviceId,
		String label,
		String documentation,
		String expectedMethod,
		String pathTemplate,
		String practiceMissionId,
		String reviewLessonId,
		List<String> alternatePracticeChallengeIds,
		boolean newlyTriggered
) {
}
