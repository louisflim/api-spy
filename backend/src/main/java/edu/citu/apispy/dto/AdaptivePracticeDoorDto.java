package edu.citu.apispy.dto;

/** Adaptive practice door offered in the current room (ungraded). */
public record AdaptivePracticeDoorDto(
		String conceptTag,
		String challengeId,
		String deviceId,
		String label,
		String documentation,
		String expectedMethod,
		String pathTemplate,
		String practiceMissionId
) {
}
