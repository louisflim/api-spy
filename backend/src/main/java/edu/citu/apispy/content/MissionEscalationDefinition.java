package edu.citu.apispy.content;

import java.util.List;

public record MissionEscalationDefinition(
		String missionId,
		List<String> mandatoryChallengeIds,
		List<String> verificationChallengeIds,
		String finalLockChallengeId
) {
}
