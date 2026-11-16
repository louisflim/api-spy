package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptStage;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.MissionProgressDto;
import edu.citu.apispy.dto.SimulateRequestDto;
import edu.citu.apispy.dto.SimulateResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConceptConsistencyServiceTest {

	private RequestSimulationService service;
	private ConceptProgressService conceptProgress;
	private ConceptConsistencyService consistency;
	private ContractCatalogService contracts;
	private MissionProgressService missionProgress;

	@BeforeEach
	void setUp() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		service = bundle.service();
		conceptProgress = bundle.conceptProgress();
		consistency = bundle.conceptConsistency();
		contracts = bundle.contracts();
		missionProgress = bundle.missionProgress();
	}

	@Test
	void correctingFailedChallengeDoesNotEarnIndependentSuccessOrImproving() {
		SimulateRequestDto primary = correctSelectedPrimary("cons-retry");
		SimulateResponseDto miss = service.simulate(
				"cons-retry",
				request(primary.missionId(), primary.deviceId(), primary.challengeId(), "POST", primary.path()));
		assertFalse(miss.correct());
		assertTrue(miss.firstAttemptRecorded());
		assertNull(miss.autoPracticeOffer());
		assertFalse(miss.conceptConsistency().isEmpty());
		assertEquals(0, miss.conceptConsistency().getFirst().consecutiveSuccesses());

		SimulateResponseDto retry = service.simulate("cons-retry", primary);
		assertTrue(retry.correct());
		assertFalse(retry.firstAttemptRecorded());
		assertFalse(conceptProgress.hasAtLeast("cons-retry", "http.method.get", ConceptStage.IMPROVING));
		assertEquals(
				0,
				consistency.openForParticipant("cons-retry", "m1-recruit-l1").getFirst().consecutiveSuccesses());
	}

	@Test
	void distinctCorrectFirstAttemptCountsAsOneOfTwo() {
		String player = "cons-one";
		SimulateRequestDto primary = correctSelectedPrimary(player);
		service.simulate(player, request(primary.missionId(), primary.deviceId(), primary.challengeId(), "POST", primary.path()));
		service.simulate(player, primary);

		var next = missionProgress.getOrCreate(player, "m1-recruit-l1").activeTerminalChallenge();
		assertNotNull(next);
		assertNotEquals(primary.challengeId(), next.challengeId());
		ApiContract nextContract = contracts.requireByChallengeId(next.challengeId());
		SimulateResponseDto second = service.simulate(player, correctFor(nextContract));
		assertTrue(second.correct());
		assertTrue(second.firstAttemptRecorded());
		assertTrue(second.controlFeedback().messages().stream()
				.anyMatch(m -> m.contains("1/" + ConceptConsistencyService.REQUIRED_CONSECUTIVE_SUCCESSES)));
		assertEquals(1, consistency.openForParticipant(player, "m1-recruit-l1").getFirst().consecutiveSuccesses());
		assertFalse(conceptProgress.hasAtLeast(player, "http.method.get", ConceptStage.IMPROVING));
	}

	@Test
	void twoConsecutiveDistinctCorrectFirstAttemptsMarkImproving() {
		String player = "cons-two";
		SimulateRequestDto primary = correctSelectedPrimary(player);
		service.simulate(player, request(primary.missionId(), primary.deviceId(), primary.challengeId(), "POST", primary.path()));
		service.simulate(player, primary);

		ApiContract second = contracts.requireByChallengeId(
				missionProgress.getOrCreate(player, "m1-recruit-l1").activeTerminalChallenge().challengeId());
		SimulateResponseDto ok2 = service.simulate(player, correctFor(second));
		assertTrue(ok2.correct());

		ApiContract third = contracts.requireByChallengeId(
				missionProgress.getOrCreate(player, "m1-recruit-l1").activeTerminalChallenge().challengeId());
		assertNotEquals(second.getChallengeId(), third.getChallengeId());
		SimulateResponseDto ok3 = service.simulate(player, correctFor(third));
		assertTrue(ok3.correct());
		assertTrue(ok3.controlFeedback().messages().stream()
				.anyMatch(m -> m.toLowerCase().contains("consistent improvement")));
		assertTrue(conceptProgress.hasAtLeast(player, "http.method.get", ConceptStage.IMPROVING));
		assertTrue(consistency.openForParticipant(player, "m1-recruit-l1").isEmpty());
	}

	@Test
	void incorrectEligibleObservationResetsStreak() {
		String player = "cons-reset";
		SimulateRequestDto primary = correctSelectedPrimary(player);
		service.simulate(player, request(primary.missionId(), primary.deviceId(), primary.challengeId(), "POST", primary.path()));
		service.simulate(player, primary);

		ApiContract second = contracts.requireByChallengeId(
				missionProgress.getOrCreate(player, "m1-recruit-l1").activeTerminalChallenge().challengeId());
		service.simulate(player, correctFor(second));
		assertEquals(1, consistency.openForParticipant(player, "m1-recruit-l1").getFirst().consecutiveSuccesses());

		ApiContract third = contracts.requireByChallengeId(
				missionProgress.getOrCreate(player, "m1-recruit-l1").activeTerminalChallenge().challengeId());
		SimulateResponseDto miss = service.simulate(
				player,
				request(
						third.getMissionId(),
						third.getDeviceId(),
						third.getChallengeId(),
						"POST",
						resolvePath(third)));
		assertFalse(miss.correct());
		assertEquals(0, consistency.openForParticipant(player, "m1-recruit-l1").getFirst().consecutiveSuccesses());
		assertTrue(miss.controlFeedback().messages().stream()
				.anyMatch(m -> m.toLowerCase().contains("reset")));
	}

	@Test
	void repeatedSubmissionDoesNotInflateEvidence() {
		String player = "cons-dup";
		SimulateRequestDto primary = correctSelectedPrimary(player);
		service.simulate(player, request(primary.missionId(), primary.deviceId(), primary.challengeId(), "POST", primary.path()));
		service.simulate(player, primary);
		ApiContract second = contracts.requireByChallengeId(
				missionProgress.getOrCreate(player, "m1-recruit-l1").activeTerminalChallenge().challengeId());
		SimulateResponseDto first = service.simulate(player, correctFor(second));
		assertEquals(1, consistency.openForParticipant(player, "m1-recruit-l1").getFirst().consecutiveSuccesses());
		SimulateResponseDto again = service.simulate(player, correctFor(second));
		assertTrue(again.correct());
		assertFalse(again.firstAttemptRecorded());
		assertEquals(1, consistency.openForParticipant(player, "m1-recruit-l1").getFirst().consecutiveSuccesses());
		assertEquals(first.challengeId(), again.challengeId());
	}

	@Test
	void refreshPreservesObservationStateAndPlannedSequence() {
		String player = "cons-refresh";
		SimulateRequestDto primary = correctSelectedPrimary(player);
		MissionProgressDto before = missionProgress.getOrCreate(player, "m1-recruit-l1");
		service.simulate(player, request(primary.missionId(), primary.deviceId(), primary.challengeId(), "POST", primary.path()));
		var openBefore = consistency.openForParticipant(player, "m1-recruit-l1").getFirst();
		MissionProgressDto after = missionProgress.getOrCreate(player, "m1-recruit-l1");
		assertEquals(before.roomGeneration(), after.roomGeneration());
		assertEquals(before.mandatoryDevices().getFirst().challengeId(), after.mandatoryDevices().getFirst().challengeId());
		var openAfter = consistency.openForParticipant(player, "m1-recruit-l1").getFirst();
		assertEquals(openBefore.plannedChallengeIds(), openAfter.plannedChallengeIds());
		assertEquals(openBefore.consecutiveSuccesses(), openAfter.consecutiveSuccesses());
	}

	@Test
	void mandatoryExitReadyWithoutFinishingConsistencyObservations() {
		String player = "cons-exit";
		SimulateRequestDto primary = correctSelectedPrimary(player);
		service.simulate(player, request(primary.missionId(), primary.deviceId(), primary.challengeId(), "POST", primary.path()));
		SimulateResponseDto unlock = service.simulate(player, primary);
		assertTrue(unlock.missionProgress().exitReady());
		assertFalse(consistency.openForParticipant(player, "m1-recruit-l1").isEmpty());
	}

	@Test
	void plannedAdditionalScenariosAreBoundedAndDeterministic() {
		UUID seed = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
		var first = consistency.planAdditionalScenarios(
				"m1-recruit-l1",
				"http.method.get",
				"chal-m1-get-door-v1",
				seed);
		var again = consistency.planAdditionalScenarios(
				"m1-recruit-l1",
				"http.method.get",
				"chal-m1-get-door-v1",
				seed);
		assertEquals(first, again);
		assertTrue(first.size() <= ConceptConsistencyService.MAX_ADDITIONAL_SCENARIOS);
		assertFalse(first.contains("chal-m1-get-door-v1"));
	}

	private SimulateRequestDto correctSelectedPrimary(String participantCode) {
		MissionProgressDto initial = missionProgress.getOrCreate(participantCode, "m1-recruit-l1");
		return correctFor(contracts.requireByChallengeId(initial.mandatoryDevices().getFirst().challengeId()));
	}

	private static SimulateRequestDto correctFor(ApiContract contract) {
		return request(
				contract.getMissionId(),
				contract.getDeviceId(),
				contract.getChallengeId(),
				contract.getExpectedMethod(),
				resolvePath(contract));
	}

	private static String resolvePath(ApiContract contract) {
		String path = contract.getPathTemplate();
		for (Map.Entry<String, String> entry : contract.getPathParameters().entrySet()) {
			path = path.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		if (!contract.getQueryParameters().isEmpty()) {
			StringBuilder query = new StringBuilder("?");
			boolean first = true;
			for (Map.Entry<String, String> entry : contract.getQueryParameters().entrySet()) {
				if (!first) {
					query.append('&');
				}
				query.append(entry.getKey()).append('=').append(entry.getValue());
				first = false;
			}
			path = path + query;
		}
		return path;
	}

	private static SimulateRequestDto request(
			String missionId,
			String deviceId,
			String challengeId,
			String method,
			String path) {
		return new SimulateRequestDto(
				"participant",
				missionId,
				deviceId,
				challengeId,
				method,
				path,
				Map.of(),
				"",
				"MISSION");
	}
}
