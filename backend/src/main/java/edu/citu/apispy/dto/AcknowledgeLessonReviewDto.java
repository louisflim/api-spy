package edu.citu.apispy.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for clearing the forced ungraded lesson gate after 15 unresolved API-room attempts. */
public record AcknowledgeLessonReviewDto(
		@NotBlank String lessonId
) {
}
