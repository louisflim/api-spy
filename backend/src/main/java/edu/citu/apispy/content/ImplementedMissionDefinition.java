package edu.citu.apispy.content;

import java.util.List;

public record ImplementedMissionDefinition(
		String missionId,
		int module,
		String rank,
		int level,
		String title,
		String status,
		String primaryChallengeId,
		List<String> requiredLessonIds,
		List<String> conceptTags,
		String scenarioFocus
) {
}
