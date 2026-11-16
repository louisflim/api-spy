package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptDecision;
import edu.citu.apispy.domain.learning.FirstAttemptRecord;
import edu.citu.apispy.domain.mission.MissionDefinition;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.domain.simulation.ApiRequest;
import edu.citu.apispy.domain.simulation.DeviceStateChange;
import edu.citu.apispy.domain.simulation.SimulatedHttpResponse;
import edu.citu.apispy.domain.simulation.SimulationMode;
import edu.citu.apispy.dto.AdaptivePracticeDoorDto;
import edu.citu.apispy.dto.ConceptConsistencyDto;
import edu.citu.apispy.dto.ConceptReviewItemDto;
import edu.citu.apispy.dto.ControlFeedbackDto;
import edu.citu.apispy.dto.DeviceStateChangeDto;
import edu.citu.apispy.dto.FirstAttemptRecordDto;
import edu.citu.apispy.dto.MissionProgressDto;
import edu.citu.apispy.dto.SimulateRequestDto;
import edu.citu.apispy.dto.SimulateResponseDto;
import edu.citu.apispy.dto.SimulatedHttpResponseDto;
import edu.citu.apispy.dto.TerminalVerificationDto;
import edu.citu.apispy.validation.SimulatedRequestValidator;
import edu.citu.apispy.validation.ValidationResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Evaluates fictional student requests against authored contracts.
 * Never performs outbound HTTP using player-entered URLs.
 * MISSION mode records first attempts and may unlock devices.
 * PRACTICE / TRAINING modes are educational only.
 */
@Service
public class RequestSimulationService {

	private final ContractCatalogService contractCatalogService;
	private final MissionCatalogService missionCatalogService;
	private final SimulatedRequestValidator simulatedRequestValidator;
	private final FirstAttemptService firstAttemptService;
	private final ConceptProgressService conceptProgressService;
	private final ConceptDecisionAnalyzer conceptDecisionAnalyzer;
	private final ConceptDecisionRecorder conceptDecisionRecorder;
	private final TerminalVerificationService terminalVerificationService;
	private final AgentFeedbackService agentFeedbackService;
	private final AdaptivePracticeService adaptivePracticeService;
	private final ConceptReviewService conceptReviewService;
	private final PracticeOutcomeService practiceOutcomeService;
	private final MissionProgressService missionProgressService;
	private final DeviceEscalationService deviceEscalationService;
	private final ScenarioSelectionService scenarioSelectionService;
	private final ConceptConsistencyService conceptConsistencyService;

	public RequestSimulationService(
			ContractCatalogService contractCatalogService,
			MissionCatalogService missionCatalogService,
			SimulatedRequestValidator simulatedRequestValidator,
			FirstAttemptService firstAttemptService,
			ConceptProgressService conceptProgressService,
			ConceptDecisionAnalyzer conceptDecisionAnalyzer,
			ConceptDecisionRecorder conceptDecisionRecorder,
			TerminalVerificationService terminalVerificationService,
			AgentFeedbackService agentFeedbackService,
			AdaptivePracticeService adaptivePracticeService,
			ConceptReviewService conceptReviewService,
			PracticeOutcomeService practiceOutcomeService,
			MissionProgressService missionProgressService,
			DeviceEscalationService deviceEscalationService,
			ScenarioSelectionService scenarioSelectionService,
			ConceptConsistencyService conceptConsistencyService) {
		this.contractCatalogService = contractCatalogService;
		this.missionCatalogService = missionCatalogService;
		this.simulatedRequestValidator = simulatedRequestValidator;
		this.firstAttemptService = firstAttemptService;
		this.conceptProgressService = conceptProgressService;
		this.conceptDecisionAnalyzer = conceptDecisionAnalyzer;
		this.conceptDecisionRecorder = conceptDecisionRecorder;
		this.terminalVerificationService = terminalVerificationService;
		this.agentFeedbackService = agentFeedbackService;
		this.adaptivePracticeService = adaptivePracticeService;
		this.conceptReviewService = conceptReviewService;
		this.practiceOutcomeService = practiceOutcomeService;
		this.missionProgressService = missionProgressService;
		this.deviceEscalationService = deviceEscalationService;
		this.scenarioSelectionService = scenarioSelectionService;
		this.conceptConsistencyService = conceptConsistencyService;
	}

	/**
	 * Read-only TVP snapshot for a graded mission terminal (no evidence writes).
	 * Ungraded practice/training missions are rejected.
	 */
	public TerminalVerificationDto terminalVerificationSnapshot(String authenticatedPlayerId, String missionId) {
		if (contractCatalogService.isUngradedContextMission(missionId)) {
			throw new IllegalArgumentException("Terminal verification applies to graded missions only");
		}
		MissionDefinition mission = missionCatalogService.requirePlayable(missionId);
		MissionProgressDto progress = missionProgressService.getOrCreate(authenticatedPlayerId, missionId);
		List<String> selectedIds = progress.mandatoryDevices() == null
				? List.of()
				: progress.mandatoryDevices().stream()
						.map(MissionProgressDto.MandatoryDeviceDto::challengeId)
						.toList();
		ApiContract primary = selectedIds.isEmpty()
				? contractCatalogService.requireByChallengeId(mission.getPrimaryChallengeId())
				: contractCatalogService.requireByChallengeId(selectedIds.getFirst());
		String terminalId = primary.getDeviceId();
		boolean mandatoryUnlocked = progress.unlockedDeviceIds() != null && !progress.unlockedDeviceIds().isEmpty();
		return terminalVerificationService.summarize(
				authenticatedPlayerId,
				mission.getMissionId(),
				terminalId,
				primary,
				mandatoryUnlocked,
				scenarioSelectionService.verificationPlan(
						missionId,
						selectedIds,
						parseRoomGeneration(progress.roomGeneration())));
	}

	public SimulateResponseDto simulate(String authenticatedPlayerId, SimulateRequestDto requestDto) {
		SimulationMode mode = SimulationMode.fromNullable(requestDto.mode());
		ApiContract contract = resolveContract(requestDto, mode);
		adaptivePracticeService.assertAuthorizedPracticeOrTraining(
				mode.name(),
				contract);

		ApiRequest apiRequest = new ApiRequest(
				requestDto.method(),
				requestDto.path(),
				requestDto.headers(),
				requestDto.body());

		ValidationResult result = simulatedRequestValidator.validate(apiRequest, contract);
		List<ConceptDecision> decisions = conceptDecisionAnalyzer.analyze(contract, result);

		if (!mode.isGraded()) {
			return buildUngradedResponse(authenticatedPlayerId, contract, apiRequest, result, decisions, mode);
		}

		MissionDefinition mission = missionCatalogService.requirePlayable(contract.getMissionId());
		missionProgressService.assertLessonGateClear(authenticatedPlayerId, mission.getMissionId());
		missionProgressService.recordGradedAttempt(authenticatedPlayerId, mission.getMissionId());
		MissionProgressDto progressBefore = missionProgressService.getOrCreate(
				authenticatedPlayerId,
				mission.getMissionId());
		boolean replayRun = progressBefore.replayActive();
		List<String> selectedIds = progressBefore.mandatoryDevices() == null
				? List.of()
				: progressBefore.mandatoryDevices().stream()
						.map(MissionProgressDto.MandatoryDeviceDto::challengeId)
						.toList();
		/*
		 * Physical device unlock only for selected mandatory challenges.
		 * TVP / consistency follow-ups marked optionalVerification must never unlock
		 * another mandatory boss device (even when the follow-up contract reuses a
		 * selected challenge id).
		 */
		boolean selectedMandatoryChallenge = selectedIds.contains(contract.getChallengeId());
		boolean activeOptional = progressBefore.activeTerminalChallenge() != null
				&& progressBefore.activeTerminalChallenge().optionalVerification()
				&& contract.getChallengeId()
						.equals(progressBefore.activeTerminalChallenge().challengeId());
		boolean optionalVerification = !selectedMandatoryChallenge || activeOptional;
		String terminalId = resolveTerminalId(mission, selectedIds);
		if (!result.isCorrect()) {
			missionProgressService.recordIncorrectGradedAttempt(authenticatedPlayerId, mission.getMissionId());
		}

		// Replay must not mutate immutable first-clear RCA records.
		FirstAttemptService.CaptureResult capture = replayRun
				? firstAttemptService.viewWithoutRecording(
						authenticatedPlayerId, contract, apiRequest, result)
				: firstAttemptService.captureIfAbsent(
						authenticatedPlayerId, contract, apiRequest, result);

		conceptDecisionRecorder.record(
				authenticatedPlayerId,
				mission,
				contract,
				decisions,
				apiRequest.getMethod() + " " + apiRequest.getPath() + Instant.now(),
				capture.newlyRecorded());

		List<ConceptConsistencyService.ObservationOutcome> consistencyOutcomes = new ArrayList<>();
		java.util.UUID roomGeneration = parseRoomGeneration(progressBefore.roomGeneration());
		for (String conceptTag : contract.getConceptTags()) {
			conceptProgressService.markPracticing(authenticatedPlayerId, conceptTag);
			if (!replayRun && capture.newlyRecorded()) {
				Boolean conceptOk = capture.record().getConceptFirstAttemptCorrect().get(conceptTag);
				if (conceptOk != null) {
					ConceptConsistencyService.ObservationOutcome outcome =
							conceptConsistencyService.observeEligibleFirstAttempt(
									authenticatedPlayerId,
									mission.getMissionId(),
									contract,
									conceptTag,
									conceptOk,
									roomGeneration);
					if (outcome != null) {
						consistencyOutcomes.add(outcome);
					}
				}
			}
		}
		conceptProgressService.markPracticing(authenticatedPlayerId, "http.response");

		boolean blockingFailure = conceptDecisionAnalyzer.hasBlockingContractError(result);
		boolean unlockAllowed = !blockingFailure && result.isCorrect() && !optionalVerification;
		boolean optionalSuccess = optionalVerification && result.isCorrect();

		List<DeviceStateChange> changes = List.of();
		if (unlockAllowed || optionalSuccess) {
			changes = List.of(new DeviceStateChange(
					contract.getDeviceId(),
					contract.getDeviceUnlockState(),
					true));
		}

		MissionProgressDto progressAfter = progressBefore;
		if (unlockAllowed) {
			progressAfter = missionProgressService.activateDevice(
					authenticatedPlayerId,
					mission.getMissionId(),
					contract.getDeviceId(),
					false);
		}

		boolean mandatoryUnlocked = unlockAllowed
				|| optionalVerification
				|| !progressAfter.unlockedDeviceIds().isEmpty();

		// TVP required slots stay on the base authored plan (KPI definition unchanged).
		// Consistency observation scenarios extend progression only, never TVP denominators.
		List<ApiContract> tvpPlan = scenarioSelectionService.verificationPlan(
				mission.getMissionId(),
				selectedIds,
				roomGeneration);
		List<ApiContract> sequencePlan = conceptConsistencyService.augmentVerificationPlan(
				authenticatedPlayerId,
				mission.getMissionId(),
				tvpPlan);
		Set<String> completedIds = new LinkedHashSet<>(
				missionProgressService.completedTerminalChallengeIds(
						authenticatedPlayerId,
						mission.getMissionId()));
		if (result.isCorrect()) {
			// Room-local sequence only — does not rewrite RCA/TVP. Idempotent on double submit.
			progressAfter = missionProgressService.completeTerminalChallenge(
					authenticatedPlayerId,
					mission.getMissionId(),
					contract.getChallengeId(),
					sequencePlan);
			completedIds = new LinkedHashSet<>(
					missionProgressService.completedTerminalChallengeIds(
							authenticatedPlayerId,
							mission.getMissionId()));
		}

		// Preserve earned TVP; do not award new evidence slots during replay.
		// Next-scenario pointer uses the extended sequence plan; requiredSlots stay on tvpPlan.
		TerminalVerificationDto tvpBase = replayRun
				? terminalVerificationService.summarize(
						authenticatedPlayerId,
						mission.getMissionId(),
						terminalId,
						contract,
						mandatoryUnlocked,
						tvpPlan,
						completedIds)
				: terminalVerificationService.recordAndSummarize(
						authenticatedPlayerId,
						contract,
						terminalId,
						decisions,
						capture.newlyRecorded(),
						result.isCorrect(),
						mandatoryUnlocked,
						tvpPlan,
						completedIds);
		var sequenceNext = mandatoryUnlocked
				? terminalVerificationService.findNextVerification(sequencePlan, contract, completedIds)
				: null;
		TerminalVerificationDto tvp = new TerminalVerificationDto(
				tvpBase.percent(),
				tvpBase.earnedSlots(),
				tvpBase.requiredSlots(),
				tvpBase.terminalId(),
				tvpBase.complete(),
				sequenceNext);

		List<AdaptivePracticeDoorDto> practiceDoors = List.of();
		if (!replayRun) {
			// Recurring-difficulty may enqueue later review; physical practice doors stay empty.
			practiceDoors = adaptivePracticeService.evaluateAfterMandatoryFirstAttempt(
					authenticatedPlayerId,
					mission,
					contract,
					capture);
		}

		// Wrong graded answers stay on the same scenario: CONTROL explains the concept error.
		// No automatic practice assignment, mini-lesson, or optional physical practice doors.
		ControlFeedbackDto feedback = agentFeedbackService.withPracticeSupport(
				agentFeedbackService.build(
						result,
						decisions,
						capture.newlyRecorded(),
						capture.record().isFirstAttemptCorrect(),
						mode),
				practiceDoors,
				null,
				consistencyOutcomes);

		List<ConceptReviewItemDto> reviews = conceptReviewService.pendingForParticipant(authenticatedPlayerId);
		List<ConceptConsistencyDto> consistency = conceptConsistencyService.openForParticipant(
				authenticatedPlayerId,
				mission.getMissionId());

		SimulatedHttpResponse response = result.isCorrect()
				? SimulatedHttpResponse.success(contract)
				: SimulatedHttpResponse.error(
						400,
						"Request does not match the documented fictional contract.",
						errorDetails(result.getErrorCodes(), apiRequest));

		return buildResponse(
				result.isCorrect(),
				contract,
				toErrorNames(result.getErrorCodes()),
				response,
				changes,
				capture,
				feedback,
				tvp,
				practiceDoors,
				null,
				optionalVerification,
				reviews,
				consistency,
				progressAfter);
	}

	private String resolveTerminalId(MissionDefinition mission) {
		return resolveTerminalId(mission, List.of());
	}

	private String resolveTerminalId(MissionDefinition mission, List<String> selectedIds) {
		if (selectedIds != null && !selectedIds.isEmpty()) {
			String primaryId = selectedIds.getFirst();
			if (!primaryId.startsWith("chal-m1-final-lock-")) {
				return contractCatalogService.requireByChallengeId(primaryId).getDeviceId();
			}
		}
		ApiContract primary = contractCatalogService.requireByChallengeId(mission.getPrimaryChallengeId());
		return primary.getDeviceId();
	}

	private SimulateResponseDto buildUngradedResponse(
			String playerId,
			ApiContract contract,
			ApiRequest apiRequest,
			ValidationResult result,
			List<ConceptDecision> decisions,
			SimulationMode mode) {
		for (String conceptTag : contract.getConceptTags()) {
			conceptProgressService.markPracticing(playerId, conceptTag);
		}
		conceptProgressService.markPracticing(playerId, "http.response");

		// PRACTICE outcomes are recorded separately and never award TVP / first-attempt evidence.
		if (mode == SimulationMode.PRACTICE) {
			String primaryConcept = contract.getConceptTags().isEmpty()
					? contract.getPrimaryConceptTag()
					: contract.getConceptTags().getFirst();
			practiceOutcomeService.record(
					playerId,
					primaryConcept,
					contract.getChallengeId(),
					null,
					result.isCorrect());
			if (result.isCorrect()) {
				// Queue later review; one correct practice does not declare Improving/mastery.
				conceptReviewService.enqueue(playerId, primaryConcept, contract.getMissionId());
			}
		}

		SimulatedHttpResponse response = result.isCorrect()
				? SimulatedHttpResponse.success(contract)
				: SimulatedHttpResponse.error(
						400,
						mode == SimulationMode.TRAINING
								? "Training request does not match the academy contract."
								: "Practice request does not match the drill contract.",
						errorDetails(result.getErrorCodes(), apiRequest));

		FirstAttemptRecordDto placeholder = new FirstAttemptRecordDto(
				playerId,
				contract.getMissionId(),
				contract.getDeviceId(),
				contract.getChallengeId(),
				contract.getVariantId(),
				contract.getConceptTags(),
				apiRequest.getMethod(),
				apiRequest.getPath(),
				apiRequest.getHeaders(),
				apiRequest.getBody(),
				toErrorNames(result.getErrorCodes()),
				result.isCorrect(),
				Map.of(),
				Instant.now().toString());

		ControlFeedbackDto feedback = agentFeedbackService.build(
				result,
				decisions,
				false,
				result.isCorrect(),
				mode);
		if (mode == SimulationMode.PRACTICE && result.isCorrect()) {
			List<String> messages = new java.util.ArrayList<>(feedback.messages());
			messages.add(
					"Ungraded practice successful. Outcome recorded separately from research evidence. "
							+ "One correct practice attempt does not mark this concept Improving.");
			feedback = new ControlFeedbackDto(feedback.tone(), List.copyOf(messages), feedback.conceptTags());
		}

		List<ConceptReviewItemDto> reviews = conceptReviewService.pendingForParticipant(playerId);

		return new SimulateResponseDto(
				result.isCorrect(),
				contract.getPrimaryConceptTag(),
				contract.getConceptTags(),
				contract.getChallengeId(),
				contract.getVariantId(),
				toErrorNames(result.getErrorCodes()),
				toDto(response),
				List.of(),
				false,
				placeholder,
				false,
				feedback,
				null,
				List.of(),
				null,
				false,
				reviews,
				List.of(),
				null);
	}

	private ApiContract resolveContract(SimulateRequestDto requestDto, SimulationMode mode) {
		if (!mode.isGraded()) {
			if (requestDto.challengeId() == null || requestDto.challengeId().isBlank()) {
				throw new IllegalArgumentException("challengeId is required for " + mode + " mode");
			}
			return contractCatalogService.requireByChallengeId(requestDto.challengeId());
		}

		MissionDefinition mission = missionCatalogService.requirePlayable(requestDto.missionId());
		if (requestDto.challengeId() != null && !requestDto.challengeId().isBlank()) {
			ApiContract contract = contractCatalogService.requireByChallengeId(requestDto.challengeId());
			if (!mission.getMissionId().equals(contract.getMissionId())) {
				throw new IllegalArgumentException("Challenge does not belong to mission: " + requestDto.missionId());
			}
			if (contractCatalogService.isUngradedContextMission(contract.getMissionId())) {
				throw new IllegalArgumentException("Cannot use training/practice contracts in MISSION mode");
			}
			return contract;
		}
		if (mission.getPrimaryChallengeId() != null) {
			return contractCatalogService.requireByChallengeId(mission.getPrimaryChallengeId());
		}
		return contractCatalogService.requireByDeviceId(requestDto.deviceId());
	}

	private SimulateResponseDto buildResponse(
			boolean correct,
			ApiContract contract,
			List<String> errorCodes,
			SimulatedHttpResponse response,
			List<DeviceStateChange> changes,
			FirstAttemptService.CaptureResult capture,
			ControlFeedbackDto feedback,
			TerminalVerificationDto tvp,
			List<AdaptivePracticeDoorDto> practiceDoors,
			AdaptivePracticeDoorDto autoPracticeOffer,
			boolean optionalVerification,
			List<ConceptReviewItemDto> reviews,
			List<ConceptConsistencyDto> consistency,
			MissionProgressDto missionProgress) {
		return new SimulateResponseDto(
				correct,
				contract.getPrimaryConceptTag(),
				contract.getConceptTags(),
				contract.getChallengeId(),
				contract.getVariantId(),
				errorCodes,
				toDto(response),
				changes.stream().map(RequestSimulationService::toDto).toList(),
				false,
				toDto(capture.record()),
				capture.newlyRecorded(),
				feedback,
				tvp,
				practiceDoors,
				autoPracticeOffer,
				optionalVerification,
				reviews,
				consistency == null ? List.of() : consistency,
				missionProgress);
	}

	private static java.util.UUID parseRoomGeneration(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return java.util.UUID.fromString(value);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static List<String> toErrorNames(Set<? extends Enum<?>> errorCodes) {
		return errorCodes.stream().map(Enum::name).sorted().collect(Collectors.toList());
	}

	private static Map<String, Object> errorDetails(
			Set<? extends Enum<?>> errorCodes,
			ApiRequest request) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("errorCodes", toErrorNames(errorCodes));
		details.put("receivedMethod", request.getMethod());
		details.put("receivedPath", request.getPath());
		details.put("receivedQuery", request.getQueryParameters());
		details.put(
				"hint",
				"Compare your method, resource path, and required parameters with the terminal documentation.");
		return details;
	}

	private static SimulatedHttpResponseDto toDto(SimulatedHttpResponse response) {
		return new SimulatedHttpResponseDto(response.getStatus(), response.getHeaders(), response.getBody());
	}

	private static DeviceStateChangeDto toDto(DeviceStateChange change) {
		return new DeviceStateChangeDto(change.getDeviceId(), change.getState(), change.isUnlocked());
	}

	private static FirstAttemptRecordDto toDto(FirstAttemptRecord record) {
		return new FirstAttemptRecordDto(
				record.getParticipantCode(),
				record.getMissionId(),
				record.getDeviceId(),
				record.getChallengeId(),
				record.getVariantId(),
				record.getConceptTags(),
				record.getMethod(),
				record.getPath(),
				record.getHeaders(),
				record.getBody(),
				record.getErrorCodes(),
				record.isFirstAttemptCorrect(),
				record.getConceptFirstAttemptCorrect(),
				record.getSubmittedAt().toString());
	}
}
