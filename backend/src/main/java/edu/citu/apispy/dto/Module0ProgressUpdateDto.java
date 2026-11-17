package edu.citu.apispy.dto;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Module0ProgressUpdateDto(
		@NotBlank @Size(max = InputLimits.ID) String topicId,
		@Size(max = InputLimits.ID) String stepId,
		Boolean markTopicComplete,
		@Size(max = InputLimits.ID) String lastTopicId
) {
	public Module0ProgressUpdateDto {
		topicId = InputSanitizer.id(topicId);
		stepId = InputSanitizer.optionalText(stepId);
		lastTopicId = InputSanitizer.optionalText(lastTopicId);
		if (lastTopicId == null || lastTopicId.isBlank()) {
			lastTopicId = topicId;
		}
	}
}
