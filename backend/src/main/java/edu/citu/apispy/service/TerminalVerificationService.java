package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptDecision;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.TerminalVerificationDto;
import edu.citu.apispy.dto.VerifyAnotherScenarioDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Awards unique first-eligible verification evidence slots and computes TVP%.
 * Earned progress never decreases. Corrected retries do not earn evidence.
 */
@Service
public class TerminalVerificationService {

	private final TerminalVerificationStore store;
	private final ContractCatalogService contractCatalogService;
	private final DeviceEscalationService deviceEscalationService;

	public TerminalVerificationService(
			TerminalVerificationStore store,
			ContractCatalogService contractCatalogService,
			DeviceEscalationService deviceEscalationService) {
		this.store = store;
		this.contractCatalogService = contractCatalogService;
		this.deviceEscalationService = deviceEscalationService;
	}

	/** Unit-test helper without JPA. */
	public static TerminalVerificationService inMemory(
			ContractCatalogService contracts,
			DeviceEscalationService escalation) {
		return new TerminalVerificationService(
				new InMemoryTerminalVerificationStore(),
				contracts,
				escalation);
	}

	public TerminalVerificationDto recordAndSummarize(
			String participantCode,
			ApiContract contract,
			String terminalId,
			List<ConceptDecision> decisions,
			boolean firstEligibleSubmission,
			boolean overallContractSuccess,
			boolean mandatoryUnlocked) {
		// Blocking failures award no TVP. Correct concepts only earn on first-eligible success
		// (or authored nonblocking partial success where overallContractSuccess is true).
		if (firstEligibleSubmission && overallContractSuccess) {
			for (ConceptDecision decision : decisions) {
				if (!decision.applicable() || !decision.correct()) {
					continue;
				}
				store.insertIfAbsent(new TerminalVerificationStore.EvidenceRow(
						participantCode,
						contract.getMissionId(),
						terminalId,
						contract.getChallengeId(),
						decision.conceptTag()));
			}
		}

		return summarize(participantCode, contract.getMissionId(), terminalId, contract, mandatoryUnlocked);
	}

	public TerminalVerificationDto summarize(
			String participantCode,
			String missionId,
			String terminalId,
			ApiContract currentContract,
			boolean mandatoryUnlocked) {
		List<ApiContract> plan = verificationPlan(missionId);
		int required = 0;
		for (ApiContract challenge : plan) {
			required += challenge.getConceptTags().size();
		}
		long earned = store.countForTerminal(participantCode, missionId, terminalId);
		int earnedSlots = (int) Math.min(Integer.MAX_VALUE, earned);
		int percent = required <= 0 ? 0 : Math.min(100, (int) Math.floor(100.0 * earnedSlots / required));
		boolean complete = required > 0 && earnedSlots >= required;

		VerifyAnotherScenarioDto next = null;
		if (mandatoryUnlocked && !complete) {
			next = findOptionalVerification(plan, currentContract, participantCode, missionId);
		}

		return new TerminalVerificationDto(percent, earnedSlots, required, terminalId, complete, next);
	}

	public List<ApiContract> verificationPlan(String missionId) {
		List<ApiContract> fromEscalation = deviceEscalationService.verificationContracts(missionId);
		if (!fromEscalation.isEmpty()) {
			return fromEscalation;
		}
		List<ApiContract> contracts = contractCatalogService.findByMissionId(missionId);
		List<ApiContract> graded = new ArrayList<>();
		for (ApiContract contract : contracts) {
			if (!contractCatalogService.isUngradedContextMission(contract.getMissionId())
					&& !contract.getDeviceId().startsWith("final-lock-")) {
				graded.add(contract);
			}
		}
		return List.copyOf(graded);
	}

	private VerifyAnotherScenarioDto findOptionalVerification(
			List<ApiContract> plan,
			ApiContract currentContract,
			String participantCode,
			String missionId) {
		for (ApiContract candidate : plan) {
			if (candidate.getChallengeId().equals(currentContract.getChallengeId())) {
				continue;
			}
			boolean missingEvidence = false;
			for (String tag : candidate.getConceptTags()) {
				boolean present = store
						.find(participantCode, missionId, candidate.getChallengeId(), tag)
						.isPresent();
				if (!present) {
					missingEvidence = true;
					break;
				}
			}
			if (missingEvidence) {
				return new VerifyAnotherScenarioDto(
						candidate.getChallengeId(),
						candidate.getVariantId(),
						candidate.getDeviceId(),
						candidate.getDocumentation());
			}
		}
		return null;
	}
}
