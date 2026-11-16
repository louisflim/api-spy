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
		/**
		 * Reserved for optional field-practice tooling. Graded wrong answers do not auto-assign
		 * practice — players remain on the same scenario with CONTROL concept feedback.
		 * Null in the normal mission wrong-answer flow.
		 */
		AdaptivePracticeDoorDto autoPracticeOffer,
		boolean optionalVerification,
		List<ConceptReviewItemDto> conceptReviewQueue,
		/**
		 * Open consistent-improvement observation cycles (not TVP / RCA / mastery claims).
		 */
		List<ConceptConsistencyDto> conceptConsistency,
		MissionProgressDto missionProgress
) {
}
