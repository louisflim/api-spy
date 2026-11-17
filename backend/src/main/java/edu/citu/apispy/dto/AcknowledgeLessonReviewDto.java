package edu.citu.apispy.dto;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for clearing the forced ungraded lesson gate after 15 unresolved API-room attempts. */
public record AcknowledgeLessonReviewDto(
		@NotBlank @Size(max = InputLimits.ID) String lessonId
) {
	public AcknowledgeLessonReviewDto {
		lessonId = InputSanitizer.id(lessonId);
	}
}
