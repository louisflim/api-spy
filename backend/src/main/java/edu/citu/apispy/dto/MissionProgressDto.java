package edu.citu.apispy.dto;

import java.util.List;

public record MissionProgressDto(
		String missionId,
		int module,
		String rank,
		String status,
		int apiResetCount,
		/** Opaque id for the active room attempt; required on complete. */
		String roomGeneration,
		int requiredDeviceCount,
		boolean finalLockRequired,
		List<String> requiredDeviceIds,
		List<String> unlockedDeviceIds,
		boolean exitReady,
		/** Permanent career clear — never cleared by replay. */
		boolean completed,
		String completedAt,
		String updatedAt,
		int gradedAttemptCountSinceReset,
		boolean combatRestartAuthorized,
		/** PRIMARY | REPLAY | NONE */
		String activeRunKind,
		boolean replayActive,
		/** Career-cleared and no active replay — Ops Board may offer Replay Operation. */
		boolean replayable,
		int replayCount,
		/** Consecutive unresolved API-driven room resets (not wrong submissions). */
		int consecutiveUnresolvedApiResets,
		/** SRS v1.3 gate after 15 consecutive unresolved API-driven room attempts. */
		boolean lessonReviewRequired,
		String requiredReviewLessonId,
		/**
		 * Server hint for Restart Mission UI: true when a manual restart would be
		 * reclassified as an API-driven abandonment and increase the device tier.
		 */
		boolean manualRestartWouldEscalate,
		/** Mandatory device count after the next escalating API reset (capped at 5). */
		int nextRequiredDeviceCount,
		List<MandatoryDeviceDto> mandatoryDevices,
		List<AdaptivePracticeDoorDto> adaptivePracticeDoors,
		/** Current terminal scenario in the automatic verification sequence. */
		ActiveTerminalChallengeDto activeTerminalChallenge,
		/** Level 7 boss state; null for missions without a boss. */
		BossStateDto boss
) {
	/**
	 * Boss encounter state. {@code shieldDown} is true once every mandatory API device is
	 * activated; only then can the boss take damage and its defeat be recorded. The exit
	 * ({@code exitReady}) stays closed until {@code defeated}.
	 */
	public record BossStateDto(boolean shieldDown, boolean defeated) {
	}

	/** Public mandatory-device brief — no expected method/path answer keys. */
	public record MandatoryDeviceDto(
			String deviceId,
			String challengeId,
			String documentation,
			boolean unlocked,
			boolean finalLock
	) {
	}
}
