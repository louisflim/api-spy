package edu.citu.apispy.dto;

import java.util.List;

public record MissionSummaryDto(
		String missionId,
		int module,
		String rank,
		int level,
		String title,
		String status,
		boolean playable,
		String primaryChallengeId,
		List<String> requiredLessonIds,
		List<String> conceptTags,
		String scenarioFocus
) {
}
