package edu.citu.apispy.content;

import java.util.List;
import java.util.Map;

public record Module0TopicFile(String moduleId, String title, List<TopicDefinition> topics) {

	public record TopicDefinition(
			String id,
			int order,
			String title,
			String objective,
			String controlIntro,
			String explanation,
			WorkedExample workedExample,
			Interaction interaction,
			String completionMessage
	) {
	}

	public record WorkedExample(String title, String notes, String body) {
	}

	public record Interaction(
			String type,
			String prompt,
			List<Option> options,
			String correctOptionId,
			String successFeedback,
			String failureFeedback,
			String demoMethod,
			String demoPath,
			Integer demoStatus,
			Map<String, Object> demoBody,
			String challengeId
	) {
	}

	public record Option(String id, String label) {
	}
}
