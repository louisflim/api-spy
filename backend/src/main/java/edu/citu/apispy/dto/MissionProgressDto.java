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
		List<MandatoryDeviceDto> mandatoryDevices,
		List<AdaptivePracticeDoorDto> adaptivePracticeDoors
) {
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
