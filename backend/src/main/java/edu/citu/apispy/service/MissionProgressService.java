package edu.citu.apispy.service;

import edu.citu.apispy.domain.mission.ActiveRunKind;
import edu.citu.apispy.domain.mission.MissionDefinition;
import edu.citu.apispy.domain.mission.MissionProgressEntity;
import edu.citu.apispy.domain.mission.MissionProgressStatus;
import edu.citu.apispy.domain.mission.Rank;
import edu.citu.apispy.domain.mission.RoomResetReason;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.ActiveTerminalChallengeDto;
import edu.citu.apispy.dto.AdaptivePracticeDoorDto;
import edu.citu.apispy.dto.MissionProgressDto;
import edu.citu.apispy.dto.VerifyAnotherScenarioDto;
import edu.citu.apispy.repository.MissionProgressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

	/** SRS v1.3 — consecutive unresolved API-driven room attempts (not wrong submissions). */
	public static final int CONSECUTIVE_UNRESOLVED_API_RESET_THRESHOLD = 15;

	private final MissionProgressRepository repository;
	private final MissionCatalogService missionCatalogService;
	private final DeviceEscalationService deviceEscalationService;
	private final AdaptivePracticeService adaptivePracticeService;
	private final ScenarioSelectionService scenarioSelectionService;
	private final ContractCatalogService contractCatalogService;
	private final TerminalVerificationService terminalVerificationService;
	private final ConceptConsistencyService conceptConsistencyService;
	private final ObjectMapper objectMapper;
	private final ConcurrentHashMap<String, MissionProgressEntity> memory;
	private final boolean memoryMode;
	/**
	 * When true (default in production), Module 1 Recruit levels unlock sequentially.
	 * Unit fixtures and the Maven test profile disable this so isolated mission tests stay focused.
	 */
	private final boolean sequentialCareerUnlockEnabled;

	@Autowired
	public MissionProgressService(
			MissionProgressRepository repository,
			MissionCatalogService missionCatalogService,
			DeviceEscalationService deviceEscalationService,
			AdaptivePracticeService adaptivePracticeService,
			ScenarioSelectionService scenarioSelectionService,
			ContractCatalogService contractCatalogService,
			TerminalVerificationService terminalVerificationService,
			ConceptConsistencyService conceptConsistencyService,
			ObjectMapper objectMapper,
			@Value("${apispy.career.sequential-unlock:true}") boolean sequentialCareerUnlockEnabled) {
		this.repository = repository;
		this.missionCatalogService = missionCatalogService;
		this.deviceEscalationService = deviceEscalationService;
		this.adaptivePracticeService = adaptivePracticeService;
		this.scenarioSelectionService = scenarioSelectionService;
		this.contractCatalogService = contractCatalogService;
		this.terminalVerificationService = terminalVerificationService;
		this.conceptConsistencyService = conceptConsistencyService;
		this.objectMapper = objectMapper;
		this.memory = null;
		this.memoryMode = false;
		this.sequentialCareerUnlockEnabled = sequentialCareerUnlockEnabled;
	}

	private MissionProgressService(
			MissionCatalogService missionCatalogService,
			DeviceEscalationService deviceEscalationService,
			AdaptivePracticeService adaptivePracticeService,
			ScenarioSelectionService scenarioSelectionService,
			ContractCatalogService contractCatalogService,
			TerminalVerificationService terminalVerificationService,
			ConceptConsistencyService conceptConsistencyService,
			ObjectMapper objectMapper,
			boolean sequentialCareerUnlockEnabled) {
		this.repository = null;
		this.missionCatalogService = missionCatalogService;
		this.deviceEscalationService = deviceEscalationService;
		this.adaptivePracticeService = adaptivePracticeService;
		this.scenarioSelectionService = scenarioSelectionService;
		this.contractCatalogService = contractCatalogService;
		this.terminalVerificationService = terminalVerificationService;
		this.conceptConsistencyService = conceptConsistencyService;
		this.objectMapper = objectMapper;
		this.memory = new ConcurrentHashMap<>();
		this.memoryMode = true;
		this.sequentialCareerUnlockEnabled = sequentialCareerUnlockEnabled;
	}

	public static MissionProgressService inMemory(
			MissionCatalogService missions,
			DeviceEscalationService escalation,
			AdaptivePracticeService adaptivePractice,
			ScenarioSelectionService scenarioSelection,
			ContractCatalogService contracts,
			TerminalVerificationService terminalVerification,
			ConceptConsistencyService conceptConsistency,
			ObjectMapper objectMapper) {
		return inMemory(
				missions,
				escalation,
				adaptivePractice,
				scenarioSelection,
				contracts,
				terminalVerification,
				conceptConsistency,
				objectMapper,
				false);
	}

	public static MissionProgressService inMemory(
			MissionCatalogService missions,
			DeviceEscalationService escalation,
			AdaptivePracticeService adaptivePractice,
			ScenarioSelectionService scenarioSelection,
			ContractCatalogService contracts,
			TerminalVerificationService terminalVerification,
			ConceptConsistencyService conceptConsistency,
			ObjectMapper objectMapper,
			boolean sequentialCareerUnlockEnabled) {
		return new MissionProgressService(
				missions,
				escalation,
				adaptivePractice,
				scenarioSelection,
				contracts,
				terminalVerification,
				conceptConsistency,
				objectMapper,
				sequentialCareerUnlockEnabled);
	}

	@Transactional
	public MissionProgressDto getOrCreate(String participantCode, String missionId) {
		MissionDefinition mission = missionCatalogService.requirePlayable(missionId);
		assertSequentialCareerAccess(participantCode, mission);
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
		assertSequentialCareerAccess(participantCode, mission);
		MissionProgressEntity entity = findEntity(participantCode, missionId)
				.orElseThrow(() -> new IllegalStateException("No completed mission progress to replay"));
		assertLessonGateClear(entity);
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
		if (!deviceEscalationService.isMandatoryDevice(
				missionId, deviceId, entity.getApiResetCount(), readSelectedChallengeIds(entity))) {
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

	/** Operational diagnostic counter — does not affect RCA, TVP, adaptive practice, or escalation. */
	@Transactional
	public void recordIncorrectGradedAttempt(String participantCode, String missionId) {
		MissionProgressEntity entity = findEntity(participantCode, missionId)
				.orElseGet(() -> createNew(participantCode, missionCatalogService.requirePlayable(missionId)));
		if (!entity.hasActiveRoomRun()) {
			return;
		}
		entity.setIncorrectGradedAttemptsSinceReset(entity.getIncorrectGradedAttemptsSinceReset() + 1);
		entity.setUpdatedAt(Instant.now());
		save(entity);
	}

	/** Selected authored challenges for the active room run (empty when none). */
	public List<String> selectedChallengeIds(String participantCode, String missionId) {
		return findEntity(participantCode, missionId)
				.map(this::readSelectedChallengeIds)
				.orElse(List.of());
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
	 * REPLAY stays REPLAY).
	 * <p>
	 * Escalation is server-classified: {@link RoomResetReason#API_STALLED} and
	 * {@link RoomResetReason#PLAYER} with unresolved graded API activity increment
	 * {@code apiResetCount}. {@link RoomResetReason#COMBAT} and idle {@code PLAYER}
	 * restarts keep the current mandatory-device tier.
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
		assertLessonGateClear(entity);
		if (!entity.hasActiveRoomRun()) {
			throw new IllegalStateException("No active room run to reset — start a replay for completed missions");
		}
		validateRoomGenerationBinding(entity, reason, roomGeneration);
		validateResetReason(entity, reason);

		RoomResetReason effective = classifyResetReason(entity, reason);

		// Never mutate ActiveRunKind here — replay must remain replay.
		if (effective == RoomResetReason.API_STALLED) {
			int next = Math.min(
					DeviceEscalationService.MAX_API_RESETS_FOR_TIER,
					entity.getApiResetCount() + 1);
			entity.setApiResetCount(next);
			int consecutive = entity.getConsecutiveUnresolvedApiResets() + 1;
			entity.setConsecutiveUnresolvedApiResets(consecutive);
			if (consecutive >= CONSECUTIVE_UNRESOLVED_API_RESET_THRESHOLD) {
				applyLessonReviewGate(entity);
			}
		}
		if (reason == RoomResetReason.COMBAT || reason == RoomResetReason.PLAYER) {
			entity.setCombatRestartAuthorized(false);
		}

		// Rotate generation so remounts bind to a fresh temporary room instance.
		entity.setRoomGeneration(UUID.randomUUID());
		entity.setGradedAttemptCountSinceReset(0);
		entity.setIncorrectGradedAttemptsSinceReset(0);
		entity.setRequiredDeviceCount(deviceEscalationService.mandatoryDeviceCount(
				entity.getMissionId(), entity.getApiResetCount()));
		entity.setFinalLockRequired(deviceEscalationService.finalLockRequired(
				entity.getMissionId(), entity.getApiResetCount()));
		entity.setUnlockedDevicesJson(writeUnlocked(Set.of()));
		// Combat / idle PLAYER keep authored selection (no graded evidence reroll).
		// API-driven abandonment may pick another authored variant for the new generation.
		if (effective == RoomResetReason.API_STALLED) {
			reselectScenarios(entity);
		} else {
			// Same scenarios, but terminal sequence restarts with unlocks cleared.
			entity.setCompletedTerminalChallengeIdsJson("[]");
			List<String> selected = readSelectedChallengeIds(entity);
			String primary = selected.stream()
					.filter(id -> !id.startsWith("chal-m1-final-lock-"))
					.findFirst()
					.orElse(selected.isEmpty() ? null : selected.getFirst());
			entity.setActiveTerminalChallengeId(primary);
		}
		entity.setUpdatedAt(Instant.now());
		save(entity);
		return toDto(entity);
	}

	/**
	 * Clears the SRS v1.3 forced lesson gate after the player revisits the required
	 * ungraded lesson. Does not reveal graded answers or change the API tier.
	 */
	@Transactional
	public MissionProgressDto acknowledgeLessonReview(
			String participantCode,
			String missionId,
			String lessonId) {
		MissionProgressEntity entity = findEntity(participantCode, missionId)
				.orElseThrow(() -> new IllegalStateException("No mission progress for lesson review"));
		if (!entity.isLessonReviewRequired()) {
			return toDto(entity);
		}
		String required = entity.getRequiredReviewLessonId();
		if (required == null || required.isBlank() || !required.equals(lessonId)) {
			throw new IllegalArgumentException("lessonId does not match the required review lesson");
		}
		entity.setLessonReviewRequired(false);
		entity.setRequiredReviewLessonId(null);
		entity.setLessonReviewCompletedAt(Instant.now());
		entity.setConsecutiveUnresolvedApiResets(0);
		entity.setUpdatedAt(Instant.now());
		save(entity);
		return toDto(entity);
	}

	/** Rejects graded field/simulate/reset work while the forced lesson gate is active. */
	public void assertLessonGateClear(String participantCode, String missionId) {
		findEntity(participantCode, missionId).ifPresent(this::assertLessonGateClear);
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
				entity.getApiResetCount(),
				readSelectedChallengeIds(entity));
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

		// PRIMARY first clear — consecutive unresolved API-room streak ends on completion.
		entity.setStatus(MissionProgressStatus.COMPLETED);
		entity.setCompletedAt(now);
		entity.setActiveRunKind(ActiveRunKind.NONE);
		entity.setCombatRestartAuthorized(false);
		entity.setConsecutiveUnresolvedApiResets(0);
		entity.setLessonReviewRequired(false);
		entity.setRequiredReviewLessonId(null);
		entity.setUpdatedAt(now);
		save(entity);
		return toDto(entity);
	}

	/**
	 * Idempotent success progression for the mission terminal sequence.
	 * Marks the challenge completed, advances {@code activeTerminalChallengeId} to the next
	 * authored verification scenario (if any), and never re-locks devices or escalates tiers.
	 */
	@Transactional
	public MissionProgressDto completeTerminalChallenge(
			String participantCode,
			String missionId,
			String challengeId,
			List<ApiContract> verificationPlan) {
		MissionProgressEntity entity = findEntity(participantCode, missionId)
				.orElseGet(() -> createNew(participantCode, missionCatalogService.requirePlayable(missionId)));
		if (!entity.hasActiveRoomRun()) {
			return toDto(entity);
		}
		List<String> completed = new ArrayList<>(readIdList(entity.getCompletedTerminalChallengeIdsJson()));
		if (!completed.contains(challengeId)) {
			completed.add(challengeId);
			entity.setCompletedTerminalChallengeIdsJson(writeIds(completed));
		}
		ApiContract current = contractCatalogService.requireByChallengeId(challengeId);
		VerifyAnotherScenarioDto next = terminalVerificationService.findNextVerification(
				verificationPlan == null ? List.of() : verificationPlan,
				current,
				new LinkedHashSet<>(completed));
		if (next != null) {
			entity.setActiveTerminalChallengeId(next.challengeId());
		} else {
			entity.setActiveTerminalChallengeId(challengeId);
		}
		entity.setUpdatedAt(Instant.now());
		save(entity);
		return toDto(entity);
	}

	public List<String> completedTerminalChallengeIds(String participantCode, String missionId) {
		return findEntity(participantCode, missionId)
				.map(entity -> readIdList(entity.getCompletedTerminalChallengeIdsJson()))
				.orElse(List.of());
	}

	private void beginFreshRoomRun(MissionProgressEntity entity, ActiveRunKind kind, int apiResetCount) {
		entity.setActiveRunKind(kind);
		entity.setApiResetCount(apiResetCount);
		entity.setRoomGeneration(UUID.randomUUID());
		entity.setRequiredDeviceCount(deviceEscalationService.mandatoryDeviceCount(
				entity.getMissionId(), apiResetCount));
		entity.setFinalLockRequired(deviceEscalationService.finalLockRequired(
				entity.getMissionId(), apiResetCount));
		entity.setUnlockedDevicesJson(writeUnlocked(Set.of()));
		entity.setGradedAttemptCountSinceReset(0);
		entity.setIncorrectGradedAttemptsSinceReset(0);
		entity.setCombatRestartAuthorized(false);
		entity.setCompletedTerminalChallengeIdsJson("[]");
		entity.setActiveTerminalChallengeId(null);
		reselectScenarios(entity);
	}

	private void reselectScenarios(MissionProgressEntity entity) {
		List<String> previous = readSelectedChallengeIds(entity);
		if (!previous.isEmpty()) {
			entity.setPreviousChallengeIdsJson(writeIds(previous));
		}
		List<String> selected = scenarioSelectionService.selectMandatoryChallengeIds(
				entity.getMissionId(),
				entity.getApiResetCount(),
				entity.getRoomGeneration(),
				readIdList(entity.getPreviousChallengeIdsJson()));
		entity.setSelectedChallengeIdsJson(writeIds(selected));
		entity.setCompletedTerminalChallengeIdsJson("[]");
		String primary = selected.stream()
				.filter(id -> !id.startsWith("chal-m1-final-lock-"))
				.findFirst()
				.orElse(selected.isEmpty() ? null : selected.getFirst());
		entity.setActiveTerminalChallengeId(primary);
	}

	private void ensureScenariosSelected(MissionProgressEntity entity) {
		if (!readSelectedChallengeIds(entity).isEmpty()) {
			return;
		}
		reselectScenarios(entity);
		save(entity);
	}

	private void validateResetReason(MissionProgressEntity entity, RoomResetReason reason) {
		List<String> unlocked = readUnlocked(entity);
		int gradedAttempts = entity.getGradedAttemptCountSinceReset();
		if (reason == RoomResetReason.PLAYER) {
			// Pause-menu Restart Mission — authorized by active run + matching roomGeneration.
			// Escalation is decided later by {@link #classifyResetReason}.
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

	/**
	 * Manual Restart Mission with unresolved graded API work is an API-driven abandonment.
	 * Idle restarts and combat deaths do not escalate.
	 */
	RoomResetReason classifyResetReason(MissionProgressEntity entity, RoomResetReason requested) {
		if (requested == RoomResetReason.PLAYER && hasUnresolvedApiActivity(entity)) {
			return RoomResetReason.API_STALLED;
		}
		return requested;
	}

	boolean hasUnresolvedApiActivity(MissionProgressEntity entity) {
		List<String> unlocked = readUnlocked(entity);
		boolean hadApiActivity = entity.getGradedAttemptCountSinceReset() > 0 || !unlocked.isEmpty();
		if (!hadApiActivity) {
			return false;
		}
		List<String> required = deviceEscalationService.requiredDeviceIds(
				entity.getMissionId(),
				entity.getApiResetCount(),
				readSelectedChallengeIds(entity));
		if (required.isEmpty()) {
			return true;
		}
		return !unlocked.containsAll(required);
	}

	private void applyLessonReviewGate(MissionProgressEntity entity) {
		entity.setLessonReviewRequired(true);
		if (entity.getRequiredReviewLessonId() == null || entity.getRequiredReviewLessonId().isBlank()) {
			MissionDefinition mission = missionCatalogService.requirePlayable(entity.getMissionId());
			List<String> lessons = mission.getRequiredLessonIds();
			entity.setRequiredReviewLessonId(lessons.isEmpty() ? null : lessons.getFirst());
		}
	}

	private void assertLessonGateClear(MissionProgressEntity entity) {
		if (entity.isLessonReviewRequired()) {
			throw new IllegalStateException(
					"Lesson review required before continuing this mission (lessonId="
							+ entity.getRequiredReviewLessonId()
							+ ")");
		}
	}

	/**
	 * Module 1 Recruit only: Level N requires career completion of Level N-1.
	 * Field/Special Agent and other modules are unchanged.
	 */
	void assertSequentialCareerAccess(String participantCode, MissionDefinition mission) {
		if (!sequentialCareerUnlockEnabled) {
			return;
		}
		if (mission.getModule() != 1 || mission.getRank() != Rank.RECRUIT || mission.getLevel() <= 1) {
			return;
		}
		String priorMissionId = "m1-recruit-l" + (mission.getLevel() - 1);
		boolean priorCleared = findEntity(participantCode, priorMissionId)
				.map(entity -> entity.getStatus() == MissionProgressStatus.COMPLETED
						&& entity.getCompletedAt() != null)
				.orElse(false);
		if (!priorCleared) {
			throw new IllegalStateException(
					"Complete " + priorMissionId + " before accessing " + mission.getMissionId());
		}
	}

	/** Whether Module 1 Recruit Level {@code level} is career-unlocked for this player. */
	public boolean isRecruitLevelUnlocked(String participantCode, int level) {
		if (level <= 1) {
			return true;
		}
		if (!sequentialCareerUnlockEnabled) {
			return true;
		}
		String priorMissionId = "m1-recruit-l" + (level - 1);
		return findEntity(participantCode, priorMissionId)
				.map(entity -> entity.getStatus() == MissionProgressStatus.COMPLETED
						&& entity.getCompletedAt() != null)
				.orElse(false);
	}

	private MissionProgressEntity createNew(String participantCode, MissionDefinition mission) {
		assertSequentialCareerAccess(participantCode, mission);
		Instant now = Instant.now();
		int initialCount = deviceEscalationService.mandatoryDeviceCount(mission.getMissionId(), 0);
		boolean initialFinalLock = deviceEscalationService.finalLockRequired(mission.getMissionId(), 0);
		MissionProgressEntity entity = new MissionProgressEntity(
				UUID.randomUUID(),
				participantCode,
				mission.getMissionId(),
				mission.getModule(),
				mission.getRank().name(),
				MissionProgressStatus.IN_PROGRESS,
				0,
				UUID.randomUUID(),
				initialCount,
				writeUnlocked(Set.of()),
				initialFinalLock,
				0,
				false,
				ActiveRunKind.PRIMARY,
				0,
				0,
				false,
				null,
				null,
				null,
				now);
		entity.setSelectedChallengeIdsJson("[]");
		entity.setPreviousChallengeIdsJson("[]");
		entity.setIncorrectGradedAttemptsSinceReset(0);
		entity.setCompletedTerminalChallengeIdsJson("[]");
		entity.setActiveTerminalChallengeId(null);
		reselectScenarios(entity);
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
		if (entity.hasActiveRoomRun()) {
			ensureScenariosSelected(entity);
		}
		List<String> selected = readSelectedChallengeIds(entity);
		List<String> requiredIds = deviceEscalationService.requiredDeviceIds(
				entity.getMissionId(),
				entity.getApiResetCount(),
				selected);
		List<String> unlocked = readUnlocked(entity);
		boolean runExitReady = entity.hasActiveRoomRun()
				&& !requiredIds.isEmpty()
				&& unlocked.containsAll(requiredIds);
		boolean careerCompleted = entity.getStatus() == MissionProgressStatus.COMPLETED;
		// Career-cleared idle rows report exitReady for board UX; active runs use current unlocks only.
		boolean exitReady = entity.hasActiveRoomRun() ? runExitReady : careerCompleted;
		List<ApiContract> contracts = deviceEscalationService.requiredMandatoryContracts(
				entity.getMissionId(),
				entity.getApiResetCount(),
				selected);
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
		boolean manualRestartWouldEscalate = entity.hasActiveRoomRun() && hasUnresolvedApiActivity(entity);
		int nextRequiredDeviceCount = deviceEscalationService.mandatoryDeviceCount(
				entity.getMissionId(),
				Math.min(
						DeviceEscalationService.MAX_API_RESETS_FOR_TIER,
						entity.getApiResetCount() + (manualRestartWouldEscalate ? 1 : 0)));
		if (!manualRestartWouldEscalate) {
			nextRequiredDeviceCount = entity.getRequiredDeviceCount();
		}
		ActiveTerminalChallengeDto activeTerminal = resolveActiveTerminalChallenge(entity, selected, unlocked);
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
				entity.getConsecutiveUnresolvedApiResets(),
				entity.isLessonReviewRequired(),
				entity.getRequiredReviewLessonId(),
				manualRestartWouldEscalate,
				nextRequiredDeviceCount,
				devices,
				practiceDoors,
				activeTerminal);
	}

	private ActiveTerminalChallengeDto resolveActiveTerminalChallenge(
			MissionProgressEntity entity,
			List<String> selected,
			List<String> unlocked) {
		if (!entity.hasActiveRoomRun()) {
			return null;
		}
		List<String> completed = readIdList(entity.getCompletedTerminalChallengeIdsJson());
		List<ApiContract> plan = conceptConsistencyService.augmentVerificationPlan(
				entity.getParticipantCode(),
				entity.getMissionId(),
				scenarioSelectionService.verificationPlan(
						entity.getMissionId(),
						selected,
						entity.getRoomGeneration()));
		String activeId = entity.getActiveTerminalChallengeId();
		if (activeId == null || activeId.isBlank()) {
			activeId = selected.stream()
					.filter(id -> !id.startsWith("chal-m1-final-lock-"))
					.findFirst()
					.orElse(null);
		}
		if (activeId == null) {
			return null;
		}
		ApiContract contract = contractCatalogService.findByChallengeId(activeId).orElse(null);
		if (contract == null) {
			return null;
		}
		String primaryId = selected.stream()
				.filter(id -> !id.startsWith("chal-m1-final-lock-"))
				.findFirst()
				.orElse(null);
		boolean optional = primaryId != null && !activeId.equals(primaryId);
		boolean sequenceComplete = !unlocked.isEmpty()
				&& completed.contains(activeId)
				&& terminalVerificationService.findNextVerification(
						plan,
						contract,
						new LinkedHashSet<>(completed)) == null;
		return new ActiveTerminalChallengeDto(
				contract.getChallengeId(),
				contract.getVariantId(),
				contract.getDeviceId(),
				contract.getDocumentation(),
				optional,
				sequenceComplete);
	}

	@SuppressWarnings("unchecked")
	private List<String> readUnlocked(MissionProgressEntity entity) {
		return readIdList(entity.getUnlockedDevicesJson());
	}

	private List<String> readSelectedChallengeIds(MissionProgressEntity entity) {
		return readIdList(entity.getSelectedChallengeIdsJson());
	}

	@SuppressWarnings("unchecked")
	private List<String> readIdList(String json) {
		try {
			List<String> list = objectMapper.readValue(
					json == null || json.isBlank() ? "[]" : json,
					List.class);
			return list == null ? List.of() : List.copyOf(list);
		} catch (Exception ex) {
			return List.of();
		}
	}

	private String writeUnlocked(Set<String> unlocked) {
		return writeIds(List.copyOf(unlocked));
	}

	private String writeIds(List<String> ids) {
		try {
			return objectMapper.writeValueAsString(ids == null ? List.of() : List.copyOf(ids));
		} catch (Exception ex) {
			return "[]";
		}
	}

	private static String key(String participantCode, String missionId) {
		return participantCode + "|" + missionId;
	}
}
