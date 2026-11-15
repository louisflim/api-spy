package edu.citu.apispy.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record Module0ProgressUpdateDto(
		@NotBlank String topicId,
		String stepId,
		Boolean markTopicComplete,
		String lastTopicId
) {
	public Module0ProgressUpdateDto {
		if (lastTopicId == null || lastTopicId.isBlank()) {
			lastTopicId = topicId;
		}
	}
}
