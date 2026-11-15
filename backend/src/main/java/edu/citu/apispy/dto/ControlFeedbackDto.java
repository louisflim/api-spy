package edu.citu.apispy.dto;

import java.util.List;

/** CONTROL agent feedback for the current submission. */
public record ControlFeedbackDto(
		String tone,
		List<String> messages,
		List<String> conceptTags
) {
}
