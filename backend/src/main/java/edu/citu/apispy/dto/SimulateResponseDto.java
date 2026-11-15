package edu.citu.apispy.dto;

import java.util.List;

/**
 * Application response returned to React/Phaser.
 * Does not include academic KPI percentages.
 */
public record SimulateResponseDto(
		boolean correct,
		String conceptTag,
		List<String> conceptTags,
		String challengeId,
		String variantId,
		List<String> errorCodes,
		SimulatedHttpResponseDto simulatedResponse,
		List<DeviceStateChangeDto> deviceChanges,
		boolean heartsChanged,
		FirstAttemptRecordDto firstAttemptRecord,
		boolean firstAttemptRecorded,
		ControlFeedbackDto controlFeedback,
		TerminalVerificationDto terminalVerification,
		List<AdaptivePracticeDoorDto> adaptivePracticeDoors,
		boolean optionalVerification,
		List<ConceptReviewItemDto> conceptReviewQueue,
		MissionProgressDto missionProgress
) {
}
