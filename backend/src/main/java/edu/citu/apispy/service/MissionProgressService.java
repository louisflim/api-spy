package edu.citu.apispy.service;

import edu.citu.apispy.domain.mission.ActiveRunKind;
import edu.citu.apispy.domain.mission.MissionDefinition;
import edu.citu.apispy.domain.mission.MissionProgressEntity;
import edu.citu.apispy.domain.mission.MissionProgressStatus;
import edu.citu.apispy.domain.mission.RoomResetReason;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.AdaptivePracticeDoorDto;
import edu.citu.apispy.dto.MissionProgressDto;
import edu.citu.apispy.repository.MissionProgressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative escape-room mission progress with career clear + temporary replay runs.
 * <p>
 * Career {@link MissionProgressStatus#COMPLETED} and {@code completedAt} are permanent.
 * An authorized {@link ActiveRunKind#REPLAY} mints a fresh {@code roomGeneration}, clears
 * current-run unlocks, and locks the exit until devices are activated again. First-clear
 * research evidence (RCA / TVP / adaptive triggers) is not owned here — callers must skip
 * duplicate writes while {@link #isReplayActive(String, String)} is true.
 * <p>
 * <strong>Limitation:</strong> Phaser position is not verified server-side. Completion binds
 * to authenticated player, active room run, matching {@code roomGeneration}, and unlocked
 * mandatory devices for the current tier.
 */
@Service
public class MissionProgressService {

	private final MissionProgressRepository repository;
	private final MissionCatalogService missionCatalogService;
	private final DeviceEscalationService deviceEscalationService;
	private final AdaptivePracticeService adaptivePracticeService;
	private final ObjectMapper objectMapper;
	private final ConcurrentHashMap<String, MissionProgressEntity> memory;
	private final boolean memoryMode;

	@Autowired
	public MissionProgressService(
			MissionProgressRepository repository,
			MissionCatalogService missionCatalogService,
			DeviceEscalationService deviceEscalationService,
			AdaptivePracticeService adaptivePracticeService,
			ObjectMapper objectMapper) {
		this.repository = repository;
		this.missionCatalogService = missionCatalogService;
		this.deviceEscalationService = deviceEscalationService;
		this.adaptivePracticeService = adaptivePracticeService;
		this.objectMapper = objectMapper;
		this.memory = null;
		this.memoryMode = false;
	}

	private MissionProgressService(
			MissionCatalogService missionCatalogService,
			DeviceEscalationService deviceEscalationService,
			AdaptivePracticeService adaptivePracticeService,
			ObjectMapper objectMapper) {
		this.repository = null;
		this.missionCatalogService = missionCatalogService;
		this.deviceEscalationService = deviceEscalationService;
		this.adaptivePracticeService = adaptivePracticeService;
		this.objectMapper = objectMapper;
		this.memory = new ConcurrentHashMap<>();
		this.memoryMode = true;
	}

	public static MissionProgressService inMemory(
			MissionCatalogService missions,
			DeviceEscalationService escalation,
			AdaptivePracticeService adaptivePractice,
			ObjectMapper objectMapper) {
		return new MissionProgressService(missions, escalation, adaptivePractice, objectMapper);
	}

	@Transactional
	public MissionProgressDto getOrCreate(String participantCode, String missionId) {
		MissionDefinition mission = missionCatalogService.requirePlayable(missionId);
		MissionProgressEntity entity = findEntity(participantCode, missionId)
				.orElseGet(() -> createNew(participantCode, mission));
		return toDto(entity);
	}

	@Transactional(readOnly = true)
	public List<MissionProgressDto> listForParticipant(String participantCode) {
		List<MissionProgressEntity> rows = memoryMode
				? memory.values().stream().filter(e -> e.getParticipantCode().equals(participantCode)).toList()
				: repository.findByParticipantCode(participantCode);
		return rows.stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public boolean isReplayActive(String participantCode, String missionId) {
		return findEntity(participantCode, missionId).map(MissionProgressEntity::isReplayActive).orElse(false);
	}

	/**
	 * Starts a server-authorized replay for a career-cleared mission.
	 * Fresh room generation, tier-0 seed ({@code apiResetCount=0}), locked devices/exit.
	 */
	@Transactional
	public MissionProgressDto startReplay(String participantCode, String missionId) {
		MissionDefinition mission = missionCatalogService.requirePlayable(missionId);
		MissionProgressEntity entity = findEntity(participantCode, missionId)
				.orElseThrow(() -> new IllegalStateException("No completed mission progress to replay"));
		if (entity.getStatus() != MissionProgressStatus.COMPLETED || entity.getCompletedAt() == null) {
			throw new IllegalStateException("Only completed missions can be replayed");
		}
		beginFreshRoomRun(entity, ActiveRunKind.REPLAY, 0);
		entity.setReplayCount(entity.getReplayCount() + 1);
		entity.setUpdatedAt(Instant.now());
		save(entity);
		return toDto(entity);
	}

	@Transactional
	public MissionProgressDto activateDevice(
			String participantCode,
			String missionId,
			String deviceId,
			boolean optionalVerification) {
		if (optionalVerification || deviceEscalationService.isPracticeDevice(deviceId)) {
			return getOrCreate(participantCode, missionId);
		}
		MissionProgressEntity entity = findEntity(participantCode, missionId)
				.orElseGet(() -> createNew(participantCode, missionCatalogService.requirePlayable(missionId)));
		if (!entity.hasActiveRoomRun()) {
			return toDto(entity);
		}
		if (!deviceEscalationService.isMandatoryDevice(missionId, deviceId, entity.getApiResetCount())) {
			return toDto(entity);
		}
		Set<String> unlocked = new LinkedHashSet<>(readUnlocked(entity));
		unlocked.add(deviceId);
		entity.setUnlockedDevicesJson(writeUnlocked(unlocked));
		entity.setUpdatedAt(Instant.now());
		save(entity);
		return toDto(entity);
	}

	@Transactional
	public void recordGradedAttempt(String participantCode, String missionId) {
		MissionProgressEntity entity = findEntity(participantCode, missionId)
				.orElseGet(() -> createNew(participantCode, missionCatalogService.requirePlayable(missionId)));
		if (!entity.hasActiveRoomRun()) {
			return;
		}
		entity.setGradedAttemptCountSinceReset(entity.getGradedAttemptCountSinceReset() + 1);
		entity.setUpdatedAt(Instant.now());
		save(entity);
	}

	@Transactional
	public MissionProgressDto authorizeCombatRestart(String participantCode, String missionId) {
		MissionProgressEntity entity = findEntity(participantCode, missionId)
				.orElseGet(() -> createNew(participantCode, missionCatalogService.requirePlayable(missionId)));
		if (!entity.hasActiveRoomRun()) {
			throw new IllegalStateException("No active room run to authorize combat restart");
		}
		entity.setCombatRestartAuthorized(true);
		entity.setUpdatedAt(Instant.now());
		save(entity);
		return toDto(entity);
	}

	/**
	 * Authorizes a combat-only restart after a client-reported death bound to the active
	 * {@code roomGeneration}. Does not trust a client health value.
	 * <p>
	 * <strong>Limitation:</strong> the server cannot independently verify Phaser combat
	 * hits; authorization requires only an authenticated active run with a matching
	 * generation. COMBAT reset still cannot escalate the API device tier.
	 */
	@Transactional
	public MissionProgressDto reportCombatDeath(
			String participantCode,
			String missionId,
			String roomGeneration) {
		if (roomGeneration == null || roomGeneration.isBlank()) {
			throw new IllegalArgumentException("roomGeneration is required to report combat death");
		}
		MissionProgressEntity entity = findEntity(participantCode, missionId)
				.orElseThrow(() -> new IllegalStateException("No mission progress for combat death"));
		if (!entity.hasActiveRoomRun()) {
			throw new IllegalStateException("No active room run for combat death");
		}
		UUID expected;
		try {
			expected = UUID.fromString(roomGeneration.trim());
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("roomGeneration is invalid");
		}
		if (!expected.equals(entity.getRoomGeneration())) {
			throw new IllegalStateException("roomGeneration does not match the active room instance");
		}
		entity.setCombatRestartAuthorized(true);
		entity.setUpdatedAt(Instant.now());
		save(entity);
		return toDto(entity);
	}

	/**
	 * Restarts the active room run. Does not alter career completion, completedAt,
	 * first-attempt / TVP / RCA records, or {@link ActiveRunKind} (PRIMARY stays PRIMARY,
	 * REPLAY stays REPLAY). Only {@link RoomResetReason#API_STALLED} increments
	 * {@code apiResetCount}; {@link RoomResetReason#PLAYER} and {@link RoomResetReason#COMBAT}
	 * keep the current mandatory-device tier.
	 */
	@Transactional
	public MissionProgressDto resetRoom(String participantCode, String missionId, RoomResetReason reason) {
		return resetRoom(participantCode, missionId, reason, null);
	}

	@Transactional
	public MissionProgressDto resetRoom(
			String participantCode,
			String missionId,
			RoomResetReason reason,
			String roomGeneration) {
		MissionProgressEntity entity = findEntity(participantCode, missionId)
				.orElseGet(() -> createNew(participantCode, missionCatalogService.requirePlayable(missionId)));
		if (!entity.hasActiveRoomRun()) {
			throw new IllegalStateException("No active room run to reset — start a replay for completed missions");
		}
		validateRoomGenerationBinding(entity, reason, roomGeneration);
		validateResetReason(entity, reason);

		// Never mutate ActiveRunKind here — replay must remain replay.
		if (reason == RoomResetReason.API_STALLED) {
			int next = Math.min(
					DeviceEscalationService.MAX_API_RESETS_FOR_TIER,
					entity.getApiResetCount() + 1);
			entity.setApiResetCount(next);
		}
		if (reason == RoomResetReason.COMBAT || reason == RoomResetReason.PLAYER) {
			entity.setCombatRestartAuthorized(false);
		}

		// Rotate generation so remounts bind to a fresh temporary room instance.
		entity.setRoomGeneration(UUID.randomUUID());
		entity.setGradedAttemptCountSinceReset(0);
		entity.setRequiredDeviceCount(deviceEscalationService.mandatoryDeviceCount(entity.getApiResetCount()));
		entity.setFinalLockRequired(deviceEscalationService.finalLockRequired(entity.getApiResetCount()));
		entity.setUnlockedDevicesJson(writeUnlocked(Set.of()));
		entity.setUpdatedAt(Instant.now());
		save(entity);
		return toDto(entity);
	}

	private void validateRoomGenerationBinding(
			MissionProgressEntity entity,
			RoomResetReason reason,
			String roomGeneration) {
		if (reason == RoomResetReason.PLAYER) {
			if (roomGeneration == null || roomGeneration.isBlank()) {
				throw new IllegalArgumentException("roomGeneration is required for PLAYER reset");
			}
			UUID expected;
			try {
				expected = UUID.fromString(roomGeneration.trim());
			} catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("roomGeneration is invalid");
			}
			if (!expected.equals(entity.getRoomGeneration())) {
				throw new IllegalStateException("roomGeneration does not match the active room instance");
			}
			return;
		}
		// Optional binding for other reasons — reject when a stale id is supplied.
		if (roomGeneration == null || roomGeneration.isBlank()) {
			return;
		}
		UUID expected;
		try {
			expected = UUID.fromString(roomGeneration.trim());
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("roomGeneration is invalid");
		}
		if (!expected.equals(entity.getRoomGeneration())) {
			throw new IllegalStateException("roomGeneration does not match the active room instance");
		}
	}

	/**
	 * Completes the active room run. First clear sets permanent COMPLETED; replay clear
	 * ends the REPLAY run without changing career completion or completedAt.
	 */
	@Transactional
	public MissionProgressDto completeAfterExit(
			String participantCode,
			String missionId,
			String roomGeneration) {
		if (roomGeneration == null || roomGeneration.isBlank()) {
			throw new IllegalArgumentException("roomGeneration is required to complete the mission");
		}
		MissionProgressEntity entity = findEntity(participantCode, missionId)
				.orElseThrow(() -> new IllegalArgumentException("No mission progress to complete"));

		UUID expectedGeneration;
		try {
			expectedGeneration = UUID.fromString(roomGeneration.trim());
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("roomGeneration is invalid");
		}

		if (!entity.hasActiveRoomRun()) {
			if (entity.getStatus() == MissionProgressStatus.COMPLETED
					&& expectedGeneration.equals(entity.getRoomGeneration())) {
				return toDto(entity);
			}
			throw new IllegalStateException("No active room run to complete");
		}

		if (!expectedGeneration.equals(entity.getRoomGeneration())) {
			throw new IllegalStateException("roomGeneration does not match the active room instance");
		}

		List<String> requiredIds = deviceEscalationService.requiredDeviceIds(
				entity.getMissionId(),
				entity.getApiResetCount());
		List<String> unlocked = readUnlocked(entity);
		boolean exitReady = !requiredIds.isEmpty() && unlocked.containsAll(requiredIds);
		if (!exitReady) {
			throw new IllegalStateException("Cannot complete mission: mandatory devices are not all activated");
		}

		Instant now = Instant.now();
		if (entity.getActiveRunKind() == ActiveRunKind.REPLAY) {
			entity.setActiveRunKind(ActiveRunKind.NONE);
			entity.setCombatRestartAuthorized(false);
			entity.setGradedAttemptCountSinceReset(0);
			entity.setUpdatedAt(now);
			save(entity);
			return toDto(entity);
		}

		// PRIMARY first clear
		entity.setStatus(MissionProgressStatus.COMPLETED);
		entity.setCompletedAt(now);
		entity.setActiveRunKind(ActiveRunKind.NONE);
		entity.setCombatRestartAuthorized(false);
		entity.setUpdatedAt(now);
		save(entity);
		return toDto(entity);
	}

	private void beginFreshRoomRun(MissionProgressEntity entity, ActiveRunKind kind, int apiResetCount) {
		entity.setActiveRunKind(kind);
		entity.setApiResetCount(apiResetCount);
		entity.setRoomGeneration(UUID.randomUUID());
		entity.setRequiredDeviceCount(deviceEscalationService.mandatoryDeviceCount(apiResetCount));
		entity.setFinalLockRequired(deviceEscalationService.finalLockRequired(apiResetCount));
		entity.setUnlockedDevicesJson(writeUnlocked(Set.of()));
		entity.setGradedAttemptCountSinceReset(0);
		entity.setCombatRestartAuthorized(false);
	}

	private void validateResetReason(MissionProgressEntity entity, RoomResetReason reason) {
		List<String> unlocked = readUnlocked(entity);
		int gradedAttempts = entity.getGradedAttemptCountSinceReset();
		if (reason == RoomResetReason.PLAYER) {
			// Pause-menu Reset level — authorized by active run + matching roomGeneration.
			return;
		}
		if (reason == RoomResetReason.API_STALLED) {
			if (gradedAttempts <= 0 && unlocked.isEmpty()) {
				throw new IllegalStateException(
						"API_STALLED reset requires graded API activity or unlocked devices in this room");
			}
			return;
		}
		if (reason == RoomResetReason.COMBAT) {
			if (!entity.isCombatRestartAuthorized()) {
				throw new IllegalStateException(
						"COMBAT reset requires server-authorized combat death via /combat-death bound to the active roomGeneration");
			}
		}
	}

	private MissionProgressEntity createNew(String participantCode, MissionDefinition mission) {
		Instant now = Instant.now();
		MissionProgressEntity entity = new MissionProgressEntity(
				UUID.randomUUID(),
				participantCode,
				mission.getMissionId(),
				mission.getModule(),
				mission.getRank().name(),
				MissionProgressStatus.IN_PROGRESS,
				0,
				UUID.randomUUID(),
				DeviceEscalationService.TIER_INITIAL,
				writeUnlocked(Set.of()),
				false,
				0,
				false,
				ActiveRunKind.PRIMARY,
				0,
				null,
				now);
		save(entity);
		return entity;
	}

	private Optional<MissionProgressEntity> findEntity(String participantCode, String missionId) {
		if (memoryMode) {
			return Optional.ofNullable(memory.get(key(participantCode, missionId)));
		}
		return repository.findByParticipantCodeAndMissionId(participantCode, missionId);
	}

	private void save(MissionProgressEntity entity) {
		if (memoryMode) {
			memory.put(key(entity.getParticipantCode(), entity.getMissionId()), entity);
			return;
		}
		repository.save(entity);
	}

	private MissionProgressDto toDto(MissionProgressEntity entity) {
		List<String> requiredIds = deviceEscalationService.requiredDeviceIds(
				entity.getMissionId(),
				entity.getApiResetCount());
		List<String> unlocked = readUnlocked(entity);
		boolean runExitReady = entity.hasActiveRoomRun()
				&& !requiredIds.isEmpty()
				&& unlocked.containsAll(requiredIds);
		boolean careerCompleted = entity.getStatus() == MissionProgressStatus.COMPLETED;
		// Career-cleared idle rows report exitReady for board UX; active runs use current unlocks only.
		boolean exitReady = entity.hasActiveRoomRun() ? runExitReady : careerCompleted;
		List<ApiContract> contracts = deviceEscalationService.requiredMandatoryContracts(
				entity.getMissionId(),
				entity.getApiResetCount());
		List<MissionProgressDto.MandatoryDeviceDto> devices = new ArrayList<>();
		for (ApiContract contract : contracts) {
			devices.add(new MissionProgressDto.MandatoryDeviceDto(
					contract.getDeviceId(),
					contract.getChallengeId(),
					contract.getDocumentation(),
					unlocked.contains(contract.getDeviceId()),
					contract.getDeviceId().startsWith("final-lock-")));
		}
		List<AdaptivePracticeDoorDto> practiceDoors = adaptivePracticeService == null
				? List.of()
				: adaptivePracticeService.activeDoorsForRoom(
						entity.getParticipantCode(),
						entity.getMissionId());
		boolean replayActive = entity.isReplayActive();
		boolean replayable = careerCompleted && !entity.hasActiveRoomRun();
		return new MissionProgressDto(
				entity.getMissionId(),
				entity.getModule(),
				entity.getRankCode(),
				entity.getStatus().name(),
				entity.getApiResetCount(),
				entity.getRoomGeneration().toString(),
				entity.getRequiredDeviceCount(),
				entity.isFinalLockRequired(),
				requiredIds,
				unlocked,
				exitReady,
				careerCompleted,
				entity.getCompletedAt() == null ? null : entity.getCompletedAt().toString(),
				entity.getUpdatedAt().toString(),
				entity.getGradedAttemptCountSinceReset(),
				entity.isCombatRestartAuthorized(),
				entity.getActiveRunKind().name(),
				replayActive,
				replayable,
				entity.getReplayCount(),
				devices,
				practiceDoors);
	}

	@SuppressWarnings("unchecked")
	private List<String> readUnlocked(MissionProgressEntity entity) {
		try {
			List<String> list = objectMapper.readValue(entity.getUnlockedDevicesJson(), List.class);
			return list == null ? List.of() : List.copyOf(list);
		} catch (Exception ex) {
			return List.of();
		}
	}

	private String writeUnlocked(Set<String> unlocked) {
		try {
			return objectMapper.writeValueAsString(List.copyOf(unlocked));
		} catch (Exception ex) {
			return "[]";
		}
	}

	private static String key(String participantCode, String missionId) {
		return participantCode + "|" + missionId;
	}
}
