package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptDecision;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.TerminalVerificationDto;
import edu.citu.apispy.dto.VerifyAnotherScenarioDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
		return recordAndSummarize(
				participantCode,
				contract,
				terminalId,
				decisions,
				firstEligibleSubmission,
				overallContractSuccess,
				mandatoryUnlocked,
				null);
	}

	public TerminalVerificationDto recordAndSummarize(
			String participantCode,
			ApiContract contract,
			String terminalId,
			List<ConceptDecision> decisions,
			boolean firstEligibleSubmission,
			boolean overallContractSuccess,
			boolean mandatoryUnlocked,
			List<ApiContract> verificationPlanOverride) {
		return recordAndSummarize(
				participantCode,
				contract,
				terminalId,
				decisions,
				firstEligibleSubmission,
				overallContractSuccess,
				mandatoryUnlocked,
				verificationPlanOverride,
				Set.of());
	}

	public TerminalVerificationDto recordAndSummarize(
			String participantCode,
			ApiContract contract,
			String terminalId,
			List<ConceptDecision> decisions,
			boolean firstEligibleSubmission,
			boolean overallContractSuccess,
			boolean mandatoryUnlocked,
			List<ApiContract> verificationPlanOverride,
			Set<String> completedChallengeIds) {
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

		return summarize(
				participantCode,
				contract.getMissionId(),
				terminalId,
				contract,
				mandatoryUnlocked,
				verificationPlanOverride,
				completedChallengeIds);
	}

	public TerminalVerificationDto summarize(
			String participantCode,
			String missionId,
			String terminalId,
			ApiContract currentContract,
			boolean mandatoryUnlocked) {
		return summarize(participantCode, missionId, terminalId, currentContract, mandatoryUnlocked, null);
	}

	public TerminalVerificationDto summarize(
			String participantCode,
			String missionId,
			String terminalId,
			ApiContract currentContract,
			boolean mandatoryUnlocked,
			List<ApiContract> verificationPlanOverride) {
		return summarize(
				participantCode,
				missionId,
				terminalId,
				currentContract,
				mandatoryUnlocked,
				verificationPlanOverride,
				Set.of());
	}

	public TerminalVerificationDto summarize(
			String participantCode,
			String missionId,
			String terminalId,
			ApiContract currentContract,
			boolean mandatoryUnlocked,
			List<ApiContract> verificationPlanOverride,
			Set<String> completedChallengeIds) {
		List<ApiContract> plan = verificationPlanOverride == null || verificationPlanOverride.isEmpty()
				? verificationPlan(missionId)
				: verificationPlanOverride;
		int required = 0;
		for (ApiContract challenge : plan) {
			required += challenge.getConceptTags().size();
		}
		long earned = store.countForTerminal(participantCode, missionId, terminalId);
		int earnedSlots = (int) Math.min(Integer.MAX_VALUE, earned);
		int percent = required <= 0 ? 0 : Math.min(100, (int) Math.floor(100.0 * earnedSlots / required));
		boolean complete = required > 0 && earnedSlots >= required;

		Set<String> completed = completedChallengeIds == null ? Set.of() : completedChallengeIds;
		VerifyAnotherScenarioDto next = null;
		// Advance through authored plan after mandatory unlock. Sequence completion (no next)
		// is independent of TVP% — wrong-first challenges still advance without looping back.
		if (mandatoryUnlocked) {
			next = findNextVerification(plan, currentContract, completed);
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

	/**
	 * Next authored verification scenario after the current one that has not already been
	 * successfully completed this room generation. Never returns the identical challenge.
	 */
	VerifyAnotherScenarioDto findNextVerification(
			List<ApiContract> plan,
			ApiContract currentContract,
			Set<String> completedChallengeIds) {
		Set<String> completed = completedChallengeIds == null
				? Set.of()
				: new HashSet<>(completedChallengeIds);
		String currentId = currentContract == null ? null : currentContract.getChallengeId();
		for (ApiContract candidate : plan) {
			String id = candidate.getChallengeId();
			if (id.equals(currentId) || completed.contains(id)) {
				continue;
			}
			return new VerifyAnotherScenarioDto(
					candidate.getChallengeId(),
					candidate.getVariantId(),
					candidate.getDeviceId(),
					candidate.getDocumentation());
		}
		return null;
	}
}
