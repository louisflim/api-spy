package edu.citu.apispy.dto;

import java.util.List;

public record LessonDto(
		String id,
		int module,
		int order,
		String title,
		List<String> conceptTags,
		String explanation,
		WorkedExampleDto workedExample,
		PracticeScenarioDto practiceScenario,
		boolean graded
) {
	public record WorkedExampleDto(
			String title,
			String notes,
			String method,
			String path,
			int responseStatus,
			Object responseBody
	) {
	}

	public record PracticeScenarioDto(
			String summary,
			String linkedMissionId,
			boolean ungraded
	) {
	}
}
