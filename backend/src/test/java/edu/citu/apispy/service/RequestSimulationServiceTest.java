package edu.citu.apispy.service;

import edu.citu.apispy.dto.SimulateRequestDto;
import edu.citu.apispy.dto.SimulateResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestSimulationServiceTest {

	private RequestSimulationService service;
	private FirstAttemptService firstAttemptService;

	@BeforeEach
	void setUp() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		service = bundle.service();
		firstAttemptService = bundle.firstAttempts();
	}

	@Test
	void correctGetRequestUnlocksDoorWithoutChangingHearts() {
		SimulateResponseDto response = service.simulate("player-test", request(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"GET",
				"/api/doors/alpha"));

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
		SimulateResponseDto response = service.simulate("player-test", request(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"POST",
				"/api/doors/alpha"));

		assertFalse(response.correct());
		assertEquals(400, response.simulatedResponse().status());
		assertTrue(response.errorCodes().contains("METHOD"));
		assertTrue(response.deviceChanges().isEmpty());
		assertFalse(response.heartsChanged());
		assertEquals(0, response.terminalVerification().percent());
	}

	@Test
	void incorrectPathReturnsEndpointError() {
		SimulateResponseDto response = service.simulate("player-test", request(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"GET",
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
		service.simulate("player-test", request(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"POST",
				"/api/doors/alpha"));

		SimulateResponseDto second = service.simulate("player-test", request(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"GET",
				"/api/doors/alpha"));

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
		SimulateResponseDto response = service.simulate("player-a", request(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"GET",
				"/api/doors/wrong"));

		assertFalse(response.correct());
		assertTrue(response.errorCodes().contains("ENDPOINT"));
		assertTrue(response.deviceChanges().isEmpty());
	}

	@Test
	void optionalVerificationCompletesAfterMandatoryUnlockWithoutBlockingExit() {
		SimulateResponseDto mandatory = service.simulate("player-opt", request(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"GET",
				"/api/doors/alpha"));
		assertTrue(mandatory.correct());
		assertNotNull(mandatory.terminalVerification().verifyAnotherScenario());

		SimulateResponseDto optional = service.simulate("player-opt", request(
				"m1-recruit-l1",
				"door-bravo",
				"chal-m1-get-door-v2",
				"GET",
				"/api/doors/bravo"));

		assertTrue(optional.correct());
		assertTrue(optional.optionalVerification());
		assertEquals(100, optional.terminalVerification().percent());
		assertNull(optional.terminalVerification().verifyAnotherScenario());
		assertTrue(optional.deviceChanges().getFirst().unlocked());
		assertEquals("door-bravo", optional.deviceChanges().getFirst().deviceId());
	}

	@Test
	void evidenceAndAdaptiveStateAreIsolatedBetweenPlayers() {
		service.simulate("player-one", request(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"GET",
				"/api/doors/alpha"));

		SimulateResponseDto other = service.simulate("player-two", request(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"POST",
				"/api/doors/alpha"));

		assertEquals(0, other.terminalVerification().percent());
		assertTrue(other.adaptivePracticeDoors().isEmpty());
		assertEquals(1, firstAttemptService.findAllForParticipant("player-one").size());
		assertEquals(1, firstAttemptService.findAllForParticipant("player-two").size());
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
