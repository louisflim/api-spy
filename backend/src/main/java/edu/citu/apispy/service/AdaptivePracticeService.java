package edu.citu.apispy.service;

import edu.citu.apispy.config.AdaptiveLearningProperties;
import edu.citu.apispy.domain.learning.AdaptiveTriggerEntity;
import edu.citu.apispy.domain.learning.FirstAttemptRecord;
import edu.citu.apispy.domain.mission.MissionDefinition;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.AdaptivePracticeDoorDto;
import edu.citu.apispy.dto.LearningSupportOfferDto;
import edu.citu.apispy.repository.AdaptiveTriggerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Cross-room adaptive practice: threshold on distinct mandatory first failures,
 * max practice doors per room, server-side PRACTICE authorization, and recovery
 * state for CONTROL targeted teaching / voluntary practice rounds.
 */
@Service
public class AdaptivePracticeService {

	private final AdaptiveLearningProperties properties;
	private final FirstAttemptService firstAttemptService;
	private final MissionCatalogService missionCatalogService;
	private final ContractCatalogService contractCatalogService;
	private final ConceptReviewService conceptReviewService;
	private final AdaptiveTriggerStore store;

	@Autowired
	public AdaptivePracticeService(
			AdaptiveLearningProperties properties,
			FirstAttemptService firstAttemptService,
			MissionCatalogService missionCatalogService,
			ContractCatalogService contractCatalogService,
			ConceptReviewService conceptReviewService,
			AdaptiveTriggerRepository repository) {
		this.properties = properties;
		this.firstAttemptService = firstAttemptService;
		this.missionCatalogService = missionCatalogService;
		this.contractCatalogService = contractCatalogService;
		this.conceptReviewService = conceptReviewService;
		this.store = new JpaAdaptiveTriggerStore(repository);
	}

	private AdaptivePracticeService(
			AdaptiveLearningProperties properties,
			FirstAttemptService firstAttemptService,
			MissionCatalogService missionCatalogService,
			ContractCatalogService contractCatalogService,
			ConceptReviewService conceptReviewService,
			AdaptiveTriggerStore store) {
		this.properties = properties;
		this.firstAttemptService = firstAttemptService;
		this.missionCatalogService = missionCatalogService;
		this.contractCatalogService = contractCatalogService;
		this.conceptReviewService = conceptReviewService;
		this.store = store;
	}

	public static AdaptivePracticeService inMemory(
			AdaptiveLearningProperties properties,
			FirstAttemptService firstAttempts,
			MissionCatalogService missions,
			ContractCatalogService contracts,
			ConceptReviewService reviews) {
		return new AdaptivePracticeService(
				properties,
				firstAttempts,
				missions,
				contracts,
				reviews,
				new InMemoryAdaptiveTriggerStore());
	}

	/**
	 * Reject forged PRACTICE that targets non-practice contracts.
	 * Voluntary practice-* and authorized adaptive practice challenges are allowed.
	 */
	public void assertAuthorizedPracticeOrTraining(String mode, ApiContract contract) {
		if ("TRAINING".equalsIgnoreCase(mode)) {
			if (!contract.getMissionId().startsWith("m0-")) {
				throw new IllegalArgumentException("TRAINING mode requires a Module 0 training contract");
			}
			return;
		}
		if (!"PRACTICE".equalsIgnoreCase(mode)) {
			return;
		}
		if (!contractCatalogService.isUngradedContextMission(contract.getMissionId())
				|| !contract.getMissionId().startsWith("practice-")) {
			throw new IllegalArgumentException("PRACTICE mode requires an authorized practice drill contract");
		}
	}

	/**
	 * Recurring-difficulty signal after an eligible graded first-attempt miss.
	 * Physical adaptive practice doors are retired: no room objects are spawned and no
	 * adaptive-trigger rows are written. Concepts that meet the historical threshold are
	 * queued for later authored review only. Ungraded PRACTICE/TRAINING never contribute.
	 */
	public List<AdaptivePracticeDoorDto> evaluateAfterMandatoryFirstAttempt(
			String participantCode,
			MissionDefinition roomMission,
			ApiContract gradedContract,
			FirstAttemptService.CaptureResult capture) {
		if (!capture.newlyRecorded() || capture.record().isFirstAttemptCorrect()) {
			return List.of();
		}

		Map<String, Integer> failureCounts = countDistinctEligibleGradedConceptFailures(participantCode);
		for (Map.Entry<String, Boolean> entry : capture.record().getConceptFirstAttemptCorrect().entrySet()) {
			if (!Boolean.FALSE.equals(entry.getValue())) {
				continue;
			}
			String conceptTag = entry.getKey();
			int count = failureCounts.getOrDefault(conceptTag, 0);
			if (count < properties.getIncorrectAttemptThreshold()) {
				continue;
			}
			conceptReviewService.enqueue(participantCode, conceptTag, roomMission.getMissionId());
		}
		return List.of();
	}

	/** Always empty — optional physical adaptive practice doors are removed from gameplay. */
	public List<AdaptivePracticeDoorDto> activeDoorsForRoom(String participantCode, String roomMissionId) {
		return List.of();
	}

	/** Always empty — mini-lesson / door recovery offers are retired with physical doors. */
	public List<LearningSupportOfferDto> supportOffersForRoom(String participantCode, String roomMissionId) {
		return List.of();
	}

	/** Retired with physical adaptive doors — kept for API compatibility. */
	public LearningSupportOfferDto markLessonOpened(
			String participantCode,
			String roomMissionId,
			String conceptTag) {
		throw new IllegalStateException(
				"Optional adaptive practice doors and mini-lesson recovery are retired. "
						+ "Use graded mission scenarios and CONTROL concept consistency instead.");
	}

	/** Retired — graded wrong answers stay on the same scenario with CONTROL feedback only. */
	public AdaptivePracticeDoorDto suggestInlinePractice(
			String conceptTag,
			Set<String> excludeChallengeIds) {
		return null;
	}

	/**
	 * Next authored ungraded practice variant from the catalog, excluding used challenge ids.
	 * Works without a recurring-difficulty trigger (inline auto-practice loop).
	 */
	public AdaptivePracticeDoorDto nextAuthoredPractice(
			String conceptTag,
			List<String> usedChallengeIds) {
		Set<String> used = usedChallengeIds == null ? Set.of() : Set.copyOf(usedChallengeIds);
		ApiContract next = resolvePracticeForConcept(conceptTag, used);
		if (next == null) {
			return null;
		}
		List<String> nextUsed = new ArrayList<>(used);
		nextUsed.add(next.getChallengeId());
		return toDoor(
				next,
				conceptTag,
				ConceptSupportCatalog.reviewLessonId(conceptTag),
				remainingPracticeIds(conceptTag, nextUsed),
				false);
	}

	/** Retired with physical adaptive practice doors. */
	public AdaptivePracticeDoorDto advancePracticeRound(
			String participantCode,
			String roomMissionId,
			String conceptTag) {
		return advancePracticeRound(participantCode, roomMissionId, conceptTag, List.of());
	}

	public AdaptivePracticeDoorDto advancePracticeRound(
			String participantCode,
			String roomMissionId,
			String conceptTag,
			List<String> usedChallengeIds) {
		throw new IllegalStateException(
				"Optional adaptive practice doors are retired. "
						+ "Continue graded mission scenarios; CONTROL tracks consistent improvement separately.");
	}

	private List<AdaptivePracticeDoorDto> activeDoorsForRoom(
			String participantCode,
			String roomMissionId,
			Set<String> newlyTriggeredKeys) {
		return List.of();
	}

	private AdaptiveTriggerStore.TriggerRow requireRoomConcept(
			String participantCode,
			String roomMissionId,
			String conceptTag) {
		return store.findForRoom(participantCode, roomMissionId).stream()
				.filter(row -> row.conceptTag().equals(conceptTag))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"No adaptive support trigger for concept " + conceptTag + " in room " + roomMissionId));
	}

	/**
	 * Distinct graded first-attempt concept misses across challenges/devices.
	 * Includes automatic verification-sequence scenarios (e.g. door-v1 then door-v2).
	 * Excludes PRACTICE/TRAINING. Does not rewrite RCA/SCR/HLCR or TVP eligibility.
	 */
	private Map<String, Integer> countDistinctEligibleGradedConceptFailures(String participantCode) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (FirstAttemptRecord record : firstAttemptService.findAllForParticipant(participantCode)) {
			if (contractCatalogService.isUngradedContextMission(record.getMissionId())) {
				continue;
			}
			if (missionCatalogService.findById(record.getMissionId()).isEmpty()) {
				continue;
			}
			for (Map.Entry<String, Boolean> entry : record.getConceptFirstAttemptCorrect().entrySet()) {
				if (Boolean.FALSE.equals(entry.getValue())) {
					counts.merge(entry.getKey(), 1, Integer::sum);
				}
			}
		}
		return counts;
	}

	private ApiContract resolvePracticeForConcept(String conceptTag, Set<String> excludeChallengeIds) {
		for (String challengeId : ConceptSupportCatalog.practiceChallengeIds(conceptTag)) {
			if (excludeChallengeIds.contains(challengeId)) {
				continue;
			}
			ApiContract contract = safeChallenge(challengeId);
			if (contract != null) {
				return contract;
			}
		}
		return null;
	}

	private List<String> remainingPracticeIds(String conceptTag, List<String> used) {
		Set<String> usedSet = new LinkedHashSet<>(used);
		List<String> remaining = new ArrayList<>();
		for (String challengeId : ConceptSupportCatalog.practiceChallengeIds(conceptTag)) {
			if (!usedSet.contains(challengeId) && safeChallenge(challengeId) != null) {
				remaining.add(challengeId);
			}
		}
		return List.copyOf(remaining);
	}

	private ApiContract safeChallenge(String challengeId) {
		try {
			return contractCatalogService.requireByChallengeId(challengeId);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static AdaptivePracticeDoorDto toDoor(
			ApiContract practice,
			String conceptTag,
			String reviewLessonId,
			List<String> alternateIds,
			boolean newlyTriggered) {
		return new AdaptivePracticeDoorDto(
				conceptTag,
				practice.getChallengeId(),
				practice.getDeviceId(),
				"Ungraded practice · " + ConceptSupportCatalog.friendlyConcept(conceptTag),
				practice.getDocumentation(),
				practice.getExpectedMethod(),
				practice.getPathTemplate(),
				practice.getMissionId(),
				reviewLessonId,
				alternateIds,
				newlyTriggered);
	}

	private static String key(String participantCode, String conceptTag, String evidenceKey) {
		return participantCode + "|" + conceptTag + "|" + evidenceKey;
	}

	interface AdaptiveTriggerStore {
		boolean exists(String participantCode, String conceptTag, String evidenceKey);

		void insert(TriggerRow row);

		void update(TriggerRow row);

		long countForRoom(String participantCode, String roomMissionId);

		List<TriggerRow> findForRoom(String participantCode, String roomMissionId);

		record TriggerRow(
				String participantCode,
				String conceptTag,
				String roomMissionId,
				String evidenceKey,
				String practiceChallengeId,
				String reviewLessonId,
				boolean lessonOpened,
				List<String> usedPracticeChallengeIds) {

			TriggerRow withLessonOpened(boolean opened) {
				return new TriggerRow(
						participantCode,
						conceptTag,
						roomMissionId,
						evidenceKey,
						practiceChallengeId,
						reviewLessonId,
						opened,
						usedPracticeChallengeIds);
			}

			TriggerRow withPractice(String challengeId, List<String> used) {
				return new TriggerRow(
						participantCode,
						conceptTag,
						roomMissionId,
						evidenceKey,
						challengeId,
						reviewLessonId,
						lessonOpened,
						List.copyOf(used));
			}
		}
	}

	private static final class InMemoryAdaptiveTriggerStore implements AdaptiveTriggerStore {
		private final ConcurrentHashMap<String, TriggerRow> rows = new ConcurrentHashMap<>();

		@Override
		public boolean exists(String participantCode, String conceptTag, String evidenceKey) {
			return rows.containsKey(key(participantCode, conceptTag, evidenceKey));
		}

		@Override
		public void insert(TriggerRow row) {
			rows.putIfAbsent(key(row.participantCode(), row.conceptTag(), row.evidenceKey()), row);
		}

		@Override
		public void update(TriggerRow row) {
			rows.put(key(row.participantCode(), row.conceptTag(), row.evidenceKey()), row);
		}

		@Override
		public long countForRoom(String participantCode, String roomMissionId) {
			return rows.values().stream()
					.filter(row -> row.participantCode().equals(participantCode)
							&& row.roomMissionId().equals(roomMissionId))
					.count();
		}

		@Override
		public List<TriggerRow> findForRoom(String participantCode, String roomMissionId) {
			return rows.values().stream()
					.filter(row -> row.participantCode().equals(participantCode)
							&& row.roomMissionId().equals(roomMissionId))
					.toList();
		}
	}

	private static final class JpaAdaptiveTriggerStore implements AdaptiveTriggerStore {
		private final AdaptiveTriggerRepository repository;

		JpaAdaptiveTriggerStore(AdaptiveTriggerRepository repository) {
			this.repository = repository;
		}

		@Override
		public boolean exists(String participantCode, String conceptTag, String evidenceKey) {
			return repository
					.findByParticipantCodeAndConceptTagAndEvidenceKey(participantCode, conceptTag, evidenceKey)
					.isPresent();
		}

		@Override
		public void insert(TriggerRow row) {
			if (exists(row.participantCode(), row.conceptTag(), row.evidenceKey())) {
				return;
			}
			repository.save(new AdaptiveTriggerEntity(
					UUID.randomUUID(),
					row.participantCode(),
					row.conceptTag(),
					row.roomMissionId(),
					row.evidenceKey(),
					row.practiceChallengeId(),
					row.reviewLessonId(),
					row.lessonOpened(),
					encodeUsed(row.usedPracticeChallengeIds()),
					Instant.now()));
		}

		@Override
		public void update(TriggerRow row) {
			AdaptiveTriggerEntity entity = repository
					.findByParticipantCodeAndConceptTagAndEvidenceKey(
							row.participantCode(),
							row.conceptTag(),
							row.evidenceKey())
					.orElseThrow();
			entity.setLessonOpened(row.lessonOpened());
			entity.setPracticeChallengeId(row.practiceChallengeId());
			entity.setUsedPracticeChallengeIds(encodeUsed(row.usedPracticeChallengeIds()));
			repository.save(entity);
		}

		@Override
		public long countForRoom(String participantCode, String roomMissionId) {
			return repository.countByParticipantCodeAndRoomMissionId(participantCode, roomMissionId);
		}

		@Override
		public List<TriggerRow> findForRoom(String participantCode, String roomMissionId) {
			return repository.findByParticipantCodeAndRoomMissionId(participantCode, roomMissionId).stream()
					.map(entity -> new TriggerRow(
							entity.getParticipantCode(),
							entity.getConceptTag(),
							entity.getRoomMissionId(),
							entity.getEvidenceKey(),
							entity.getPracticeChallengeId(),
							entity.getReviewLessonId(),
							entity.isLessonOpened(),
							decodeUsed(entity.getUsedPracticeChallengeIds())))
					.toList();
		}

		private static String encodeUsed(List<String> ids) {
			if (ids == null || ids.isEmpty()) {
				return "[]";
			}
			return ids.stream().map(id -> id.replace(",", "")).collect(Collectors.joining(",", "[", "]"));
		}

		private static List<String> decodeUsed(String raw) {
			if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) {
				return List.of();
			}
			String trimmed = raw.trim();
			if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
				trimmed = trimmed.substring(1, trimmed.length() - 1);
			}
			if (trimmed.isBlank()) {
				return List.of();
			}
			return Arrays.stream(trimmed.split(","))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.toList();
		}
	}
}
