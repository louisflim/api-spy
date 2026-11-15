package edu.citu.apispy.domain.mission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
		name = "mission_progress",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_mission_progress_player_mission",
				columnNames = { "participant_code", "mission_id" }))
public class MissionProgressEntity {

	@Id
	private UUID id;

	@Column(name = "participant_code", nullable = false, length = 64)
	private String participantCode;

	@Column(name = "mission_id", nullable = false, length = 64)
	private String missionId;

	@Column(nullable = false)
	private int module;

	@Column(name = "rank_code", nullable = false, length = 32)
	private String rankCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private MissionProgressStatus status;

	@Column(name = "api_reset_count", nullable = false)
	private int apiResetCount;

	@Column(name = "room_generation", nullable = false)
	private UUID roomGeneration;

	@Column(name = "required_device_count", nullable = false)
	private int requiredDeviceCount;

	@Column(name = "unlocked_devices_json", nullable = false, columnDefinition = "TEXT")
	private String unlockedDevicesJson;

	@Column(name = "final_lock_required", nullable = false)
	private boolean finalLockRequired;

	@Column(name = "graded_attempt_count_since_reset", nullable = false)
	private int gradedAttemptCountSinceReset;

	@Column(name = "combat_restart_authorized", nullable = false)
	private boolean combatRestartAuthorized;

	@Enumerated(EnumType.STRING)
	@Column(name = "active_run_kind", nullable = false, length = 16)
	private ActiveRunKind activeRunKind;

	@Column(name = "replay_count", nullable = false)
	private int replayCount;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected MissionProgressEntity() {
	}

	public MissionProgressEntity(
			UUID id,
			String participantCode,
			String missionId,
			int module,
			String rankCode,
			MissionProgressStatus status,
			int apiResetCount,
			UUID roomGeneration,
			int requiredDeviceCount,
			String unlockedDevicesJson,
			boolean finalLockRequired,
			int gradedAttemptCountSinceReset,
			boolean combatRestartAuthorized,
			ActiveRunKind activeRunKind,
			int replayCount,
			Instant completedAt,
			Instant updatedAt) {
		this.id = id;
		this.participantCode = participantCode;
		this.missionId = missionId;
		this.module = module;
		this.rankCode = rankCode;
		this.status = status;
		this.apiResetCount = apiResetCount;
		this.roomGeneration = roomGeneration;
		this.requiredDeviceCount = requiredDeviceCount;
		this.unlockedDevicesJson = unlockedDevicesJson;
		this.finalLockRequired = finalLockRequired;
		this.gradedAttemptCountSinceReset = gradedAttemptCountSinceReset;
		this.combatRestartAuthorized = combatRestartAuthorized;
		this.activeRunKind = activeRunKind;
		this.replayCount = replayCount;
		this.completedAt = completedAt;
		this.updatedAt = updatedAt;
	}

	public UUID getId() {
		return id;
	}

	public String getParticipantCode() {
		return participantCode;
	}

	public String getMissionId() {
		return missionId;
	}

	public int getModule() {
		return module;
	}

	public String getRankCode() {
		return rankCode;
	}

	public MissionProgressStatus getStatus() {
		return status;
	}

	public void setStatus(MissionProgressStatus status) {
		this.status = status;
	}

	public int getApiResetCount() {
		return apiResetCount;
	}

	public void setApiResetCount(int apiResetCount) {
		this.apiResetCount = apiResetCount;
	}

	public UUID getRoomGeneration() {
		return roomGeneration;
	}

	public void setRoomGeneration(UUID roomGeneration) {
		this.roomGeneration = roomGeneration;
	}

	public int getRequiredDeviceCount() {
		return requiredDeviceCount;
	}

	public void setRequiredDeviceCount(int requiredDeviceCount) {
		this.requiredDeviceCount = requiredDeviceCount;
	}

	public String getUnlockedDevicesJson() {
		return unlockedDevicesJson;
	}

	public void setUnlockedDevicesJson(String unlockedDevicesJson) {
		this.unlockedDevicesJson = unlockedDevicesJson;
	}

	public boolean isFinalLockRequired() {
		return finalLockRequired;
	}

	public void setFinalLockRequired(boolean finalLockRequired) {
		this.finalLockRequired = finalLockRequired;
	}

	public int getGradedAttemptCountSinceReset() {
		return gradedAttemptCountSinceReset;
	}

	public void setGradedAttemptCountSinceReset(int gradedAttemptCountSinceReset) {
		this.gradedAttemptCountSinceReset = gradedAttemptCountSinceReset;
	}

	public boolean isCombatRestartAuthorized() {
		return combatRestartAuthorized;
	}

	public void setCombatRestartAuthorized(boolean combatRestartAuthorized) {
		this.combatRestartAuthorized = combatRestartAuthorized;
	}

	public ActiveRunKind getActiveRunKind() {
		return activeRunKind;
	}

	public void setActiveRunKind(ActiveRunKind activeRunKind) {
		this.activeRunKind = activeRunKind;
	}

	public int getReplayCount() {
		return replayCount;
	}

	public void setReplayCount(int replayCount) {
		this.replayCount = replayCount;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public boolean hasActiveRoomRun() {
		return activeRunKind == ActiveRunKind.PRIMARY || activeRunKind == ActiveRunKind.REPLAY;
	}

	public boolean isReplayActive() {
		return activeRunKind == ActiveRunKind.REPLAY;
	}
}
