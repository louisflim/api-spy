package edu.citu.apispy.dto;

import java.util.List;
import java.util.Map;

public record FirstAttemptRecordDto(
		String participantCode,
		String missionId,
		String deviceId,
		String challengeId,
		String variantId,
		List<String> conceptTags,
		String method,
		String path,
		Map<String, String> headers,
		String body,
		List<String> errorCodes,
		boolean firstAttemptCorrect,
		Map<String, Boolean> conceptFirstAttemptCorrect,
		String submittedAt
) {
}
