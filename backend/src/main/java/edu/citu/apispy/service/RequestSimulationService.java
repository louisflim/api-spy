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
import java.util.LinkedHashMap;
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
	private final MissionProgressService missionProgressService;
	private final DeviceEscalationService deviceEscalationService;

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
			MissionProgressService missionProgressService,
			DeviceEscalationService deviceEscalationService) {
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
		this.missionProgressService = missionProgressService;
		this.deviceEscalationService = deviceEscalationService;
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
		ApiContract primary = contractCatalogService.requireByChallengeId(mission.getPrimaryChallengeId());
		String terminalId = resolveTerminalId(mission);
		MissionProgressDto progress = missionProgressService.getOrCreate(authenticatedPlayerId, missionId);
		boolean mandatoryUnlocked = progress.unlockedDeviceIds() != null && !progress.unlockedDeviceIds().isEmpty();
		return terminalVerificationService.summarize(
				authenticatedPlayerId,
				mission.getMissionId(),
				terminalId,
				primary,
				mandatoryUnlocked);
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
		missionProgressService.recordGradedAttempt(authenticatedPlayerId, mission.getMissionId());
		MissionProgressDto progressBefore = missionProgressService.getOrCreate(
				authenticatedPlayerId,
				mission.getMissionId());
		boolean replayRun = progressBefore.replayActive();
		boolean optionalVerification = !deviceEscalationService.isMandatoryDevice(
				mission.getMissionId(),
				contract.getDeviceId(),
				progressBefore.apiResetCount());
		String terminalId = resolveTerminalId(mission);

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

		for (String conceptTag : contract.getConceptTags()) {
			conceptProgressService.markPracticing(authenticatedPlayerId, conceptTag);
			Boolean firstCorrect = capture.record().getConceptFirstAttemptCorrect().get(conceptTag);
			if (!replayRun && Boolean.FALSE.equals(firstCorrect) && result.isCorrect()) {
				conceptProgressService.markImproving(authenticatedPlayerId, conceptTag);
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

		// Preserve earned TVP; do not award new evidence slots during replay.
		TerminalVerificationDto tvp = replayRun
				? terminalVerificationService.summarize(
						authenticatedPlayerId,
						mission.getMissionId(),
						terminalId,
						contract,
						mandatoryUnlocked)
				: terminalVerificationService.recordAndSummarize(
						authenticatedPlayerId,
						contract,
						terminalId,
						decisions,
						capture.newlyRecorded(),
						result.isCorrect(),
						mandatoryUnlocked);

		List<AdaptivePracticeDoorDto> practiceDoors = List.of();
		boolean primaryMandatory = contract.getChallengeId().equals(mission.getPrimaryChallengeId());
		if (replayRun) {
			// Do not mint duplicate adaptive triggers from previously counted evidence.
			practiceDoors = adaptivePracticeService.activeDoorsForRoom(
					authenticatedPlayerId,
					mission.getMissionId());
		} else if (primaryMandatory) {
			practiceDoors = adaptivePracticeService.evaluateAfterMandatoryFirstAttempt(
					authenticatedPlayerId,
					mission,
					contract,
					capture);
		} else {
			practiceDoors = adaptivePracticeService.activeDoorsForRoom(
					authenticatedPlayerId,
					mission.getMissionId());
		}

		ControlFeedbackDto feedback = agentFeedbackService.build(
				result,
				decisions,
				capture.newlyRecorded(),
				capture.record().isFirstAttemptCorrect(),
				mode);

		List<ConceptReviewItemDto> reviews = conceptReviewService.pendingForParticipant(authenticatedPlayerId);

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
				optionalVerification,
				reviews,
				progressAfter);
	}

	private String resolveTerminalId(MissionDefinition mission) {
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
				false,
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
			boolean optionalVerification,
			List<ConceptReviewItemDto> reviews,
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
				optionalVerification,
				reviews,
				missionProgress);
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
