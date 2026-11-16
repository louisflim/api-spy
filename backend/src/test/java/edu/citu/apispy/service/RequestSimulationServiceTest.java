package edu.citu.apispy.service;

import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.MissionProgressDto;
import edu.citu.apispy.dto.SimulateRequestDto;
import edu.citu.apispy.dto.SimulateResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestSimulationServiceTest {

	private RequestSimulationService service;
	private FirstAttemptService firstAttemptService;
	private ContractCatalogService contracts;
	private MissionProgressService missionProgress;

	@BeforeEach
	void setUp() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		service = bundle.service();
		firstAttemptService = bundle.firstAttempts();
		contracts = bundle.contracts();
		missionProgress = bundle.missionProgress();
	}

	@Test
	void correctGetRequestUnlocksDoorWithoutChangingHearts() {
		SimulateResponseDto response = service.simulate("player-test", correctSelectedPrimary("player-test"));

		assertTrue(response.correct());
		assertEquals(200, response.simulatedResponse().status());
		assertEquals(1, response.deviceChanges().size());
		assertTrue(response.deviceChanges().getFirst().unlocked());
		assertFalse(response.heartsChanged());
		assertTrue(response.errorCodes().isEmpty());
		assertTrue(response.firstAttemptRecorded());
		assertNotNull(response.terminalVerification());
		assertEquals(50, response.terminalVerification().percent());
		assertNotNull(response.terminalVerification().verifyAnotherScenario());
	}

	@Test
	void incorrectMethodReturnsSimulatedErrorAndDoesNotUnlockDoor() {
		SimulateRequestDto primary = correctSelectedPrimary("player-method");
		SimulateResponseDto response = service.simulate(
				"player-method",
				request(
						primary.missionId(),
						primary.deviceId(),
						primary.challengeId(),
						"POST",
						primary.path()));

		assertFalse(response.correct());
		assertEquals(400, response.simulatedResponse().status());
		assertTrue(response.errorCodes().contains("METHOD"));
		assertTrue(response.deviceChanges().isEmpty());
		assertFalse(response.heartsChanged());
		assertEquals(0, response.terminalVerification().percent());
	}

	@Test
	void incorrectPathReturnsEndpointError() {
		SimulateRequestDto primary = correctSelectedPrimary("player-path");
		SimulateResponseDto response = service.simulate(
				"player-path",
				request(
						primary.missionId(),
						primary.deviceId(),
						primary.challengeId(),
						primary.method(),
						"/api/doors/beta"));

		assertFalse(response.correct());
		assertTrue(response.errorCodes().contains("ENDPOINT"));
		assertTrue(response.deviceChanges().isEmpty());
	}

	@Test
	void multipleErrorsAreReportedSeparately() {
		SimulateResponseDto response = service.simulate("player-test", request(
				"m1-recruit-l3",
				"sensor-panel",
				"chal-m1-query-sensor-v1",
				"POST",
				"/api/sensors"));

		assertFalse(response.correct());
		assertTrue(response.errorCodes().contains("METHOD"));
		assertTrue(response.errorCodes().contains("PARAMETER_MISSING"));
		assertTrue(response.errorCodes().contains("PARAMETER"));
	}

	@Test
	void pathParameterWrongValueIsDetected() {
		SimulateResponseDto response = service.simulate("player-test", request(
				"m1-recruit-l2",
				"lock-north",
				"chal-m1-path-lock-v1",
				"GET",
				"/api/locks/south-gate"));

		assertFalse(response.correct());
		assertTrue(response.errorCodes().contains("PARAMETER_VALUE"));
		assertTrue(response.errorCodes().contains("PARAMETER"));
	}

	@Test
	void queryParameterSuccessUnlocksDevice() {
		SimulateResponseDto response = service.simulate("player-test", request(
				"m1-recruit-l3",
				"sensor-panel",
				"chal-m1-query-sensor-v1",
				"GET",
				"/api/sensors?zone=alpha"));

		assertTrue(response.correct());
		assertTrue(response.deviceChanges().getFirst().unlocked());
	}

	@Test
	void successfulRetryDoesNotOverwriteIncorrectFirstAttempt() {
		SimulateRequestDto primary = correctSelectedPrimary("player-retry");
		service.simulate(
				"player-retry",
				request(
						primary.missionId(),
						primary.deviceId(),
						primary.challengeId(),
						"POST",
						primary.path()));

		SimulateResponseDto second = service.simulate("player-retry", primary);

		assertTrue(second.correct());
		assertFalse(second.firstAttemptRecorded());
		assertFalse(second.firstAttemptRecord().firstAttemptCorrect());
		assertEquals("POST", second.firstAttemptRecord().method());
		assertTrue(second.firstAttemptRecord().errorCodes().contains("METHOD"));
		assertTrue(second.deviceChanges().getFirst().unlocked());
		assertEquals(0, second.terminalVerification().percent());
		assertNotNull(second.controlFeedback());
	}

	@Test
	void incorrectMandatoryEndpointNeverUnlocksDoor() {
		SimulateRequestDto primary = correctSelectedPrimary("player-a");
		SimulateResponseDto response = service.simulate(
				"player-a",
				request(
						primary.missionId(),
						primary.deviceId(),
						primary.challengeId(),
						primary.method(),
						"/api/doors/wrong"));

		assertFalse(response.correct());
		assertTrue(response.errorCodes().contains("ENDPOINT"));
		assertTrue(response.deviceChanges().isEmpty());
	}

	@Test
	void correctedRetryUnlocksWithoutEvidenceButStillOffersOptionalVerification() {
		SimulateRequestDto primary = correctSelectedPrimary("player-retry-tvp");
		service.simulate(
				"player-retry-tvp",
				request(
						primary.missionId(),
						primary.deviceId(),
						primary.challengeId(),
						"POST",
						primary.path()));

		SimulateResponseDto corrected = service.simulate("player-retry-tvp", primary);

		assertTrue(corrected.correct());
		assertTrue(corrected.deviceChanges().getFirst().unlocked());
		assertEquals(0, corrected.terminalVerification().percent());
		assertEquals(0, corrected.terminalVerification().earnedSlots());
		assertEquals(4, corrected.terminalVerification().requiredSlots());
		assertNotNull(
				corrected.terminalVerification().verifyAnotherScenario(),
				"Optional concept verification must remain available after unlock without first-attempt evidence");
		assertNotNull(corrected.terminalVerification().verifyAnotherScenario());
		assertNotEquals(
				primary.challengeId(),
				corrected.terminalVerification().verifyAnotherScenario().challengeId());
	}

	@Test
	void optionalVerificationCompletesAfterMandatoryUnlockWithoutBlockingExit() {
		SimulateResponseDto mandatory = service.simulate("player-opt", correctSelectedPrimary("player-opt"));
		assertTrue(mandatory.correct());
		assertNotNull(mandatory.terminalVerification().verifyAnotherScenario());
		var next = mandatory.terminalVerification().verifyAnotherScenario();
		assertNotEquals(mandatory.challengeId(), next.challengeId());
		assertEquals(next.challengeId(), mandatory.missionProgress().activeTerminalChallenge().challengeId());

		ApiContract nextContract = contracts.requireByChallengeId(next.challengeId());
		SimulateResponseDto optional = service.simulate("player-opt", correctFor(nextContract));

		assertTrue(optional.correct());
		assertTrue(optional.optionalVerification());
		assertNull(optional.terminalVerification().verifyAnotherScenario());
		assertTrue(optional.deviceChanges().getFirst().unlocked());
		assertEquals(nextContract.getDeviceId(), optional.deviceChanges().getFirst().deviceId());
		assertTrue(optional.missionProgress().activeTerminalChallenge().sequenceComplete());
	}

	@Test
	void wrongThenCorrectAdvancesWithoutLoopingCompletedScenarios() {
		SimulateRequestDto primary = correctSelectedPrimary("player-seq");
		service.simulate(
				"player-seq",
				request(
						primary.missionId(),
						primary.deviceId(),
						primary.challengeId(),
						"POST",
						primary.path()));
		SimulateResponseDto corrected = service.simulate("player-seq", primary);
		assertTrue(corrected.correct());
		assertEquals(0, corrected.terminalVerification().percent());
		var next = corrected.terminalVerification().verifyAnotherScenario();
		assertNotNull(next);
		assertNotEquals(primary.challengeId(), next.challengeId());

		ApiContract nextContract = contracts.requireByChallengeId(next.challengeId());
		SimulateResponseDto optional = service.simulate("player-seq", correctFor(nextContract));
		assertTrue(optional.correct());
		var afterOptional = optional.terminalVerification().verifyAnotherScenario();
		if (afterOptional != null) {
			assertNotEquals(primary.challengeId(), afterOptional.challengeId());
			assertNotEquals(next.challengeId(), afterOptional.challengeId());
			assertFalse(optional.missionProgress().activeTerminalChallenge().sequenceComplete());
		} else {
			assertTrue(optional.missionProgress().activeTerminalChallenge().sequenceComplete());
		}

		// Double success on the current scenario must remain idempotent (no loop to completed ids).
		SimulateResponseDto again = service.simulate("player-seq", correctFor(nextContract));
		assertTrue(again.correct());
		var againNext = again.terminalVerification().verifyAnotherScenario();
		if (againNext != null) {
			assertNotEquals(primary.challengeId(), againNext.challengeId());
			assertNotEquals(next.challengeId(), againNext.challengeId());
		}
	}

	@Test
	void evidenceAndAdaptiveStateAreIsolatedBetweenPlayers() {
		service.simulate("player-one", correctSelectedPrimary("player-one"));

		SimulateRequestDto otherPrimary = correctSelectedPrimary("player-two");
		SimulateResponseDto other = service.simulate(
				"player-two",
				request(
						otherPrimary.missionId(),
						otherPrimary.deviceId(),
						otherPrimary.challengeId(),
						"POST",
						otherPrimary.path()));

		assertEquals(0, other.terminalVerification().percent());
		assertTrue(other.adaptivePracticeDoors().isEmpty());
		assertEquals(1, firstAttemptService.findAllForParticipant("player-one").size());
		assertEquals(1, firstAttemptService.findAllForParticipant("player-two").size());
	}

	private SimulateRequestDto correctSelectedPrimary(String participantCode) {
		MissionProgressDto initial = missionProgress.getOrCreate(participantCode, "m1-recruit-l1");
		MissionProgressDto.MandatoryDeviceDto selected = initial.mandatoryDevices().getFirst();
		return correctFor(contracts.requireByChallengeId(selected.challengeId()));
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
				"participant-001",
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
