package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptConsistencyEntity;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.ConceptConsistencyDto;
import edu.citu.apispy.repository.ConceptConsistencyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded consistent-improvement observations for Module 1 concepts.
 * Separate from TVP / RCA / SCR / HLCR. Does not claim scientifically validated mastery.
 *
 * <p>After an eligible first-attempt concept miss, a recovery cycle opens. Independent
 * successes require eligible first attempts on distinct graded scenarios. Two consecutive
 * successes mark Improving. At most {@link #MAX_ADDITIONAL_SCENARIOS} observation scenarios
 * per cycle; exhaustion queues later review without blocking exit.
 */
@Service
public class ConceptConsistencyService {

	public static final String STATUS_OPEN = "OPEN";
	public static final String STATUS_IMPROVED = "IMPROVED";
	public static final String STATUS_EXHAUSTED = "EXHAUSTED";
	public static final int REQUIRED_CONSECUTIVE_SUCCESSES = 2;
	public static final int MAX_ADDITIONAL_SCENARIOS = 3;

	public record ObservationOutcome(
			String conceptTag,
			String status,
			int consecutiveSuccesses,
			boolean opened,
			boolean improved,
			boolean exhausted,
			boolean counted
	) {
	}

	private final ConceptConsistencyRepository repository;
	private final ContractCatalogService contractCatalogService;
	private final ConceptProgressService conceptProgressService;
	private final ConceptReviewService conceptReviewService;
	private final ObjectMapper objectMapper;
	private final ConcurrentHashMap<String, ConceptConsistencyEntity> memoryRows;
	private final boolean memoryMode;

	@Autowired
	public ConceptConsistencyService(
			ConceptConsistencyRepository repository,
			ContractCatalogService contractCatalogService,
			ConceptProgressService conceptProgressService,
			ConceptReviewService conceptReviewService,
			ObjectMapper objectMapper) {
		this.repository = repository;
		this.contractCatalogService = contractCatalogService;
		this.conceptProgressService = conceptProgressService;
		this.conceptReviewService = conceptReviewService;
		this.objectMapper = objectMapper;
		this.memoryRows = null;
		this.memoryMode = false;
	}

	private ConceptConsistencyService(
			ContractCatalogService contractCatalogService,
			ConceptProgressService conceptProgressService,
			ConceptReviewService conceptReviewService,
			ObjectMapper objectMapper) {
		this.repository = null;
		this.contractCatalogService = contractCatalogService;
		this.conceptProgressService = conceptProgressService;
		this.conceptReviewService = conceptReviewService;
		this.objectMapper = objectMapper;
		this.memoryRows = new ConcurrentHashMap<>();
		this.memoryMode = true;
	}

	public static ConceptConsistencyService inMemory(
			ContractCatalogService contracts,
			ConceptProgressService progress,
			ConceptReviewService reviews,
			ObjectMapper objectMapper) {
		return new ConceptConsistencyService(contracts, progress, reviews, objectMapper);
	}

	/**
	 * Processes one eligible graded first-attempt concept decision.
	 * Retries of an already-recorded challenge never call this (caller must pass newlyRecorded only).
	 */
	@Transactional
	public ObservationOutcome observeEligibleFirstAttempt(
			String participantCode,
			String missionId,
			ApiContract contract,
			String conceptTag,
			boolean conceptCorrectOnFirstAttempt,
			UUID roomGeneration) {
		if (participantCode == null || conceptTag == null || contract == null) {
			return null;
		}
		if (contractCatalogService.isUngradedContextMission(missionId)
				|| contract.getDeviceId().startsWith("final-lock-")
				|| contract.getDeviceId().startsWith("practice-")) {
			return null;
		}
		if (!contract.getConceptTags().contains(conceptTag)) {
			return null;
		}

		Optional<ConceptConsistencyEntity> open = findOpen(participantCode, conceptTag);
		if (!conceptCorrectOnFirstAttempt) {
			if (open.isEmpty()) {
				ConceptConsistencyEntity created = openCycle(
						participantCode,
						missionId,
						conceptTag,
						contract.getChallengeId(),
						roomGeneration);
				return new ObservationOutcome(
						conceptTag,
						STATUS_OPEN,
						0,
						true,
						false,
						false,
						false);
			}
			ConceptConsistencyEntity cycle = open.get();
			if (contract.getChallengeId().equals(cycle.getSourceChallengeId())) {
				// Same failed challenge re-recorded path should not occur; ignore.
				return snapshot(cycle, false, false, false, false);
			}
			if (alreadyObserved(cycle, contract.getChallengeId())) {
				return snapshot(cycle, false, false, false, false);
			}
			return recordObservation(cycle, contract.getChallengeId(), false, missionId);
		}

		// Concept correct on this eligible first attempt.
		if (open.isEmpty()) {
			return null;
		}
		ConceptConsistencyEntity cycle = open.get();
		if (contract.getChallengeId().equals(cycle.getSourceChallengeId())) {
			// Correct first attempt on the original failed challenge cannot happen after a miss
			// was already recorded for that challenge; treat as non-independent.
			return snapshot(cycle, false, false, false, false);
		}
		if (alreadyObserved(cycle, contract.getChallengeId())) {
			return snapshot(cycle, false, false, false, false);
		}
		return recordObservation(cycle, contract.getChallengeId(), true, missionId);
	}

	/**
	 * Extends the authored verification plan with planned observation scenarios for open cycles.
	 * Deterministic and refresh-stable (planned ids are persisted when the cycle opens).
	 */
	public List<ApiContract> augmentVerificationPlan(
			String participantCode,
			String missionId,
			List<ApiContract> basePlan) {
		List<ApiContract> plan = new ArrayList<>(basePlan == null ? List.of() : basePlan);
		Set<String> present = new LinkedHashSet<>();
		for (ApiContract contract : plan) {
			present.add(contract.getChallengeId());
		}
		for (ConceptConsistencyEntity cycle : findOpenForMission(participantCode, missionId)) {
			for (String challengeId : readIds(cycle.getPlannedChallengeIdsJson())) {
				if (present.contains(challengeId)) {
					continue;
				}
				contractCatalogService.findByChallengeId(challengeId).ifPresent(contract -> {
					plan.add(contract);
					present.add(challengeId);
				});
			}
		}
		return List.copyOf(plan);
	}

	public List<ConceptConsistencyDto> openForParticipant(String participantCode, String missionId) {
		List<ConceptConsistencyDto> rows = new ArrayList<>();
		for (ConceptConsistencyEntity cycle : findOpenForMission(participantCode, missionId)) {
			rows.add(toDto(cycle));
		}
		return List.copyOf(rows);
	}

	public ConceptConsistencyDto toDto(ConceptConsistencyEntity cycle) {
		return new ConceptConsistencyDto(
				cycle.getConceptTag(),
				cycle.getStatus(),
				cycle.getConsecutiveSuccesses(),
				REQUIRED_CONSECUTIVE_SUCCESSES,
				cycle.getScenariosUsed(),
				MAX_ADDITIONAL_SCENARIOS,
				cycle.getSourceChallengeId(),
				readIds(cycle.getPlannedChallengeIdsJson()));
	}

	private ObservationOutcome recordObservation(
			ConceptConsistencyEntity cycle,
			String challengeId,
			boolean conceptCorrect,
			String missionId) {
		List<String> observed = new ArrayList<>(readIds(cycle.getObservedChallengeIdsJson()));
		observed.add(challengeId);
		cycle.setObservedChallengeIdsJson(writeIds(observed));
		cycle.setScenariosUsed(cycle.getScenariosUsed() + 1);
		if (conceptCorrect) {
			cycle.setConsecutiveSuccesses(cycle.getConsecutiveSuccesses() + 1);
		} else {
			cycle.setConsecutiveSuccesses(0);
		}
		cycle.setUpdatedAt(Instant.now());

		boolean improved = false;
		boolean exhausted = false;
		if (cycle.getConsecutiveSuccesses() >= REQUIRED_CONSECUTIVE_SUCCESSES) {
			cycle.setStatus(STATUS_IMPROVED);
			conceptProgressService.markImproving(cycle.getParticipantCode(), cycle.getConceptTag());
			improved = true;
		} else if (cycle.getScenariosUsed() >= MAX_ADDITIONAL_SCENARIOS) {
			cycle.setStatus(STATUS_EXHAUSTED);
			conceptReviewService.enqueue(cycle.getParticipantCode(), cycle.getConceptTag(), missionId);
			exhausted = true;
		}
		save(cycle);
		return snapshot(cycle, false, improved, exhausted, true);
	}

	private ConceptConsistencyEntity openCycle(
			String participantCode,
			String missionId,
			String conceptTag,
			String sourceChallengeId,
			UUID roomGeneration) {
		List<String> planned = planAdditionalScenarios(
				missionId,
				conceptTag,
				sourceChallengeId,
				roomGeneration);
		Instant now = Instant.now();
		ConceptConsistencyEntity entity = new ConceptConsistencyEntity(
				UUID.randomUUID(),
				participantCode,
				conceptTag,
				missionId,
				sourceChallengeId,
				0,
				0,
				"[]",
				writeIds(planned),
				STATUS_OPEN,
				now,
				now);
		save(entity);
		return entity;
	}

	List<String> planAdditionalScenarios(
			String missionId,
			String conceptTag,
			String sourceChallengeId,
			UUID roomGeneration) {
		List<ApiContract> candidates = contractCatalogService.findByMissionId(missionId).stream()
				.filter(c -> !contractCatalogService.isUngradedContextMission(c.getMissionId()))
				.filter(c -> !c.getDeviceId().startsWith("final-lock-"))
				.filter(c -> !c.getDeviceId().startsWith("practice-"))
				.filter(c -> c.getConceptTags().contains(conceptTag))
				.filter(c -> !c.getChallengeId().equals(sourceChallengeId))
				.toList();
		if (candidates.isEmpty()) {
			return List.of();
		}
		UUID seed = roomGeneration == null
				? UUID.nameUUIDFromBytes((missionId + "|" + conceptTag).getBytes(StandardCharsets.UTF_8))
				: roomGeneration;
		List<ApiContract> ordered = new ArrayList<>(candidates);
		ordered.sort((a, b) -> Integer.compare(
				Math.floorMod(stableHash(seed, conceptTag, a.getChallengeId()), 10_000),
				Math.floorMod(stableHash(seed, conceptTag, b.getChallengeId()), 10_000)));
		// Prefer meaningfully different device families when available.
		List<String> selected = new ArrayList<>();
		Set<String> usedDevices = new LinkedHashSet<>();
		usedDevices.add(deviceFamily(sourceChallengeId));
		for (ApiContract candidate : ordered) {
			if (selected.size() >= MAX_ADDITIONAL_SCENARIOS) {
				break;
			}
			String family = deviceFamily(candidate.getDeviceId());
			if (usedDevices.contains(family) && selected.size() + 1 < Math.min(MAX_ADDITIONAL_SCENARIOS, ordered.size())) {
				continue;
			}
			selected.add(candidate.getChallengeId());
			usedDevices.add(family);
		}
		if (selected.size() < MAX_ADDITIONAL_SCENARIOS) {
			for (ApiContract candidate : ordered) {
				if (selected.size() >= MAX_ADDITIONAL_SCENARIOS) {
					break;
				}
				if (!selected.contains(candidate.getChallengeId())) {
					selected.add(candidate.getChallengeId());
				}
			}
		}
		return List.copyOf(selected);
	}

	private static String deviceFamily(String deviceOrChallengeId) {
		if (deviceOrChallengeId == null) {
			return "";
		}
		String value = deviceOrChallengeId;
		if (value.startsWith("chal-m1-")) {
			// chal-m1-get-door-v1 → door
			String[] parts = value.split("-");
			return parts.length >= 4 ? parts[3] : value;
		}
		int dash = value.indexOf('-');
		return dash > 0 ? value.substring(0, dash) : value;
	}

	static int stableHash(UUID roomGeneration, String conceptTag, String challengeId) {
		String material = roomGeneration + "|" + conceptTag + "|" + challengeId;
		byte[] bytes = material.getBytes(StandardCharsets.UTF_8);
		int hash = 0;
		for (byte value : bytes) {
			hash = 31 * hash + (value & 0xff);
		}
		return hash;
	}

	private boolean alreadyObserved(ConceptConsistencyEntity cycle, String challengeId) {
		return readIds(cycle.getObservedChallengeIdsJson()).contains(challengeId);
	}

	private ObservationOutcome snapshot(
			ConceptConsistencyEntity cycle,
			boolean opened,
			boolean improved,
			boolean exhausted,
			boolean counted) {
		return new ObservationOutcome(
				cycle.getConceptTag(),
				cycle.getStatus(),
				cycle.getConsecutiveSuccesses(),
				opened,
				improved,
				exhausted,
				counted);
	}

	private Optional<ConceptConsistencyEntity> findOpen(String participantCode, String conceptTag) {
		if (memoryMode) {
			return memoryRows.values().stream()
					.filter(row -> row.getParticipantCode().equals(participantCode)
							&& row.getConceptTag().equals(conceptTag)
							&& STATUS_OPEN.equals(row.getStatus()))
					.findFirst();
		}
		List<ConceptConsistencyEntity> rows =
				repository.findByParticipantCodeAndConceptTagAndStatus(participantCode, conceptTag, STATUS_OPEN);
		return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
	}

	private List<ConceptConsistencyEntity> findOpenForMission(String participantCode, String missionId) {
		if (memoryMode) {
			return memoryRows.values().stream()
					.filter(row -> row.getParticipantCode().equals(participantCode)
							&& row.getMissionId().equals(missionId)
							&& STATUS_OPEN.equals(row.getStatus()))
					.toList();
		}
		return repository.findByParticipantCodeAndMissionIdAndStatus(participantCode, missionId, STATUS_OPEN);
	}

	private void save(ConceptConsistencyEntity entity) {
		if (memoryMode) {
			memoryRows.put(entity.getId().toString(), entity);
			return;
		}
		repository.save(entity);
	}

	private List<String> readIds(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			String[] values = objectMapper.readValue(json, String[].class);
			return values == null ? List.of() : List.of(values);
		} catch (Exception ex) {
			return List.of();
		}
	}

	private String writeIds(List<String> ids) {
		try {
			return objectMapper.writeValueAsString(ids == null ? List.of() : ids);
		} catch (Exception ex) {
			return "[]";
		}
	}
}
