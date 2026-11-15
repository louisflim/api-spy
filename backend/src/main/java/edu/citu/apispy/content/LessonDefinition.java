package edu.citu.apispy.content;

import java.util.List;
import java.util.Map;

public record LessonDefinition(
		String id,
		int module,
		int order,
		String title,
		List<String> conceptTags,
		String explanation,
		WorkedExampleDefinition workedExample,
		PracticeScenarioDefinition practiceScenario
) {
	public record WorkedExampleDefinition(
			String title,
			String notes,
			String method,
			String path,
			int responseStatus,
			Map<String, Object> responseBody
	) {
	}

	public record PracticeScenarioDefinition(
			String summary,
			String linkedMissionId,
			boolean ungraded
	) {
	}
}
