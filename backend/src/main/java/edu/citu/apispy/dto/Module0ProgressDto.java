package edu.citu.apispy.dto;

import java.util.List;
import java.util.Map;

public record Module0ProgressDto(
		String participantCode,
		String lastTopicId,
		List<String> completedTopicIds,
		Map<String, List<String>> completedSteps,
		boolean orientationCompleted,
		String updatedAt
) {
}
