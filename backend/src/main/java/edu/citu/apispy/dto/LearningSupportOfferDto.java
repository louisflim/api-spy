package edu.citu.apispy.dto;

import java.util.List;

/**
 * Server-side recovery snapshot for CONTROL targeted teaching / practice.
 * Practice outcomes and review availability persist independently of graded RCA/TVP.
 */
public record LearningSupportOfferDto(
		String conceptTag,
		String reviewLessonId,
		boolean lessonOpened,
		boolean recurringDifficultyActive,
		String activePracticeChallengeId,
		List<String> remainingPracticeChallengeIds,
		String roomMissionId
) {
}
