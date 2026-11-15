package edu.citu.apispy.content;

import java.util.List;
import java.util.Map;

/**
 * Jackson-friendly challenge bank row.
 */
public record ChallengeDefinition(
		String challengeId,
		String variantId,
		String missionId,
		String deviceId,
		List<String> conceptTags,
		String expectedMethod,
		String pathTemplate,
		Map<String, String> pathParameters,
		Map<String, String> queryParameters,
		int successStatus,
		Map<String, Object> successBody,
		String documentation,
		String deviceUnlockState
) {
}
