package edu.citu.apispy.dto;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PracticeOutcomeRequestDto(
		@NotBlank @Size(max = InputLimits.CONCEPT_TAG) String conceptTag,
		@NotBlank @Size(max = InputLimits.ID) String challengeId,
		@Size(max = InputLimits.ID) String roomMissionId,
		boolean correct
) {
	public PracticeOutcomeRequestDto {
		conceptTag = InputSanitizer.id(conceptTag);
		challengeId = InputSanitizer.id(challengeId);
		roomMissionId = InputSanitizer.optionalText(roomMissionId);
	}
}
