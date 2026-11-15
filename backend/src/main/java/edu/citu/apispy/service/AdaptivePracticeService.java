package edu.citu.apispy.service;

import edu.citu.apispy.config.AdaptiveLearningProperties;
import edu.citu.apispy.domain.learning.AdaptiveTriggerEntity;
import edu.citu.apispy.domain.learning.FirstAttemptRecord;
import edu.citu.apispy.domain.mission.MissionDefinition;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.AdaptivePracticeDoorDto;
import edu.citu.apispy.repository.AdaptiveTriggerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-room adaptive practice: threshold on distinct mandatory first failures,
 * max practice doors per room, server-side PRACTICE authorization.
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

	public List<AdaptivePracticeDoorDto> evaluateAfterMandatoryFirstAttempt(
			String participantCode,
			MissionDefinition roomMission,
			ApiContract mandatoryContract,
			FirstAttemptService.CaptureResult capture) {
		if (!capture.newlyRecorded() || capture.record().isFirstAttemptCorrect()) {
			return activeDoorsForRoom(participantCode, roomMission.getMissionId());
		}
		// Caller supplies the active-run primary (selected authored scenario). Catalog primary alone is not used.

		Map<String, Integer> failureCounts = countDistinctMandatoryConceptFailures(participantCode);
		List<AdaptivePracticeDoorDto> offered = new ArrayList<>();

		for (Map.Entry<String, Boolean> entry : capture.record().getConceptFirstAttemptCorrect().entrySet()) {
			if (!Boolean.FALSE.equals(entry.getValue())) {
				continue;
			}
			String conceptTag = entry.getKey();
			int count = failureCounts.getOrDefault(conceptTag, 0);
			if (count < properties.getIncorrectAttemptThreshold()) {
				continue;
			}

			String evidenceKey = capture.record().getMissionId() + ":" + capture.record().getChallengeId()
					+ ":" + conceptTag;
			if (store.exists(participantCode, conceptTag, evidenceKey)) {
				continue;
			}

			long roomDoors = store.countForRoom(participantCode, roomMission.getMissionId());
			if (roomDoors >= properties.getMaxPracticeDoorsPerRoom()) {
				conceptReviewService.enqueue(participantCode, conceptTag, roomMission.getMissionId());
				continue;
			}

			ApiContract practice = resolvePracticeForConcept(conceptTag);
			if (practice == null) {
				conceptReviewService.enqueue(participantCode, conceptTag, roomMission.getMissionId());
				continue;
			}

			store.insert(new AdaptiveTriggerStore.TriggerRow(
					participantCode,
					conceptTag,
					roomMission.getMissionId(),
					evidenceKey,
					practice.getChallengeId()));

			offered.add(toDoor(practice, conceptTag));
		}

		List<AdaptivePracticeDoorDto> active = activeDoorsForRoom(participantCode, roomMission.getMissionId());
		if (!offered.isEmpty()) {
			return active;
		}
		return active;
	}

	public List<AdaptivePracticeDoorDto> activeDoorsForRoom(String participantCode, String roomMissionId) {
		List<AdaptivePracticeDoorDto> doors = new ArrayList<>();
		for (AdaptiveTriggerStore.TriggerRow row : store.findForRoom(participantCode, roomMissionId)) {
			try {
				ApiContract practice = contractCatalogService.requireByChallengeId(row.practiceChallengeId());
				doors.add(toDoor(practice, row.conceptTag()));
			} catch (IllegalArgumentException ignored) {
				// Skip stale catalog references.
			}
		}
		return List.copyOf(doors);
	}

	private Map<String, Integer> countDistinctMandatoryConceptFailures(String participantCode) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (FirstAttemptRecord record : firstAttemptService.findAllForParticipant(participantCode)) {
			if (contractCatalogService.isUngradedContextMission(record.getMissionId())) {
				continue;
			}
			Optional<MissionDefinition> mission = missionCatalogService.findById(record.getMissionId());
			if (mission.isEmpty() || !record.getChallengeId().equals(mission.get().getPrimaryChallengeId())) {
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

	private ApiContract resolvePracticeForConcept(String conceptTag) {
		// Prefer alternate authored practice resources when available (anti-memorization).
		return switch (conceptTag) {
			case "http.method.get", "http.endpoint", "rest.fundamentals" ->
					firstPresent("chal-practice-m1-camera", "chal-practice-m1-l1");
			case "http.path_parameter" ->
					firstPresent("chal-practice-m1-panel", "chal-practice-m1-l2");
			case "http.query_parameter" ->
					firstPresent("chal-practice-m1-badge-query", "chal-practice-m1-l3");
			case "http.method.post" -> safeChallenge("chal-practice-m1-l4");
			case "http.method.put", "http.method.patch" -> safeChallenge("chal-practice-m1-l5");
			case "http.method.delete" -> safeChallenge("chal-practice-m1-l6");
			default -> null;
		};
	}

	private ApiContract firstPresent(String... challengeIds) {
		for (String challengeId : challengeIds) {
			ApiContract contract = safeChallenge(challengeId);
			if (contract != null) {
				return contract;
			}
		}
		return null;
	}

	private ApiContract safeChallenge(String challengeId) {
		try {
			return contractCatalogService.requireByChallengeId(challengeId);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static AdaptivePracticeDoorDto toDoor(ApiContract practice, String conceptTag) {
		return new AdaptivePracticeDoorDto(
				conceptTag,
				practice.getChallengeId(),
				practice.getDeviceId(),
				"Ungraded practice · " + friendlyConcept(conceptTag),
				practice.getDocumentation(),
				practice.getExpectedMethod(),
				practice.getPathTemplate(),
				practice.getMissionId());
	}

	private static String friendlyConcept(String conceptTag) {
		return switch (conceptTag) {
			case "http.method.get" -> "GET requests";
			case "http.method.post" -> "POST requests";
			case "http.method.put" -> "PUT requests";
			case "http.method.patch" -> "PATCH requests";
			case "http.method.delete" -> "DELETE requests";
			case "http.endpoint" -> "API endpoints";
			case "http.path_parameter" -> "path parameters";
			case "http.query_parameter" -> "query parameters";
			case "rest.fundamentals" -> "REST fundamentals";
			default -> conceptTag;
		};
	}

	interface AdaptiveTriggerStore {
		boolean exists(String participantCode, String conceptTag, String evidenceKey);

		void insert(TriggerRow row);

		long countForRoom(String participantCode, String roomMissionId);

		List<TriggerRow> findForRoom(String participantCode, String roomMissionId);

		record TriggerRow(
				String participantCode,
				String conceptTag,
				String roomMissionId,
				String evidenceKey,
				String practiceChallengeId) {
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

		private static String key(String participantCode, String conceptTag, String evidenceKey) {
			return participantCode + "|" + conceptTag + "|" + evidenceKey;
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
					Instant.now()));
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
							entity.getPracticeChallengeId()))
					.toList();
		}
	}
}
