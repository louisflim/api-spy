package edu.citu.apispy.content;

import java.util.List;

/**
 * Escalation + authored scenario pools for Module 1.
 * {@code mandatoryChallengeIds} remains the default ordered fallback.
 * When {@code mandatoryScenarioPools} is present, each slot selects from its pool.
 */
public record MissionEscalationDefinition(
		String missionId,
		List<String> mandatoryChallengeIds,
		List<String> verificationChallengeIds,
		String finalLockChallengeId,
		List<List<String>> mandatoryScenarioPools,
		List<String> verificationScenarioPool
) {
	public MissionEscalationDefinition {
		if (mandatoryChallengeIds == null) {
			mandatoryChallengeIds = List.of();
		}
		if (verificationChallengeIds == null) {
			verificationChallengeIds = List.of();
		}
	}

	/** Backward-compatible constructor for tests without pools. */
	public MissionEscalationDefinition(
			String missionId,
			List<String> mandatoryChallengeIds,
			List<String> verificationChallengeIds,
			String finalLockChallengeId) {
		this(missionId, mandatoryChallengeIds, verificationChallengeIds, finalLockChallengeId, null, null);
	}
}
