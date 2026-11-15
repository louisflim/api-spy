package edu.citu.apispy.service;

import edu.citu.apispy.dto.SimulateRequestDto;
import edu.citu.apispy.dto.SimulateResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationModeServiceTest {

	private RequestSimulationService service;
	private FirstAttemptService firstAttemptService;

	@BeforeEach
	void setUp() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		service = bundle.service();
		firstAttemptService = bundle.firstAttempts();
	}

	@Test
	void trainingModeDoesNotRecordFirstAttemptOrUnlockDevices() {
		SimulateResponseDto response = service.simulate(
				"player-train",
				new SimulateRequestDto(
						"ignored",
						"m0-training-room",
						"training-terminal",
						"chal-m0-training-terminal",
						"GET",
						"/api/training/lockers/academy",
						Map.of(),
						"",
						"TRAINING"));

		assertTrue(response.correct());
		assertFalse(response.firstAttemptRecorded());
		assertTrue(response.deviceChanges().isEmpty());
		assertFalse(response.heartsChanged());
		assertTrue(firstAttemptService.findAllForParticipant("player-train").isEmpty());
	}

	@Test
	void practiceModeRejectsGradedMissionContracts() {
		try {
			service.simulate(
					"player-practice",
					new SimulateRequestDto(
							"ignored",
							"practice-m1-recruit-l1",
							"door-alpha",
							"chal-m1-get-door-v1",
							"GET",
							"/api/doors/alpha",
							Map.of(),
							"",
							"PRACTICE"));
			assertFalse(true, "Expected rejection of graded contract in PRACTICE mode");
		} catch (IllegalArgumentException ex) {
			assertTrue(ex.getMessage().toLowerCase().contains("practice"));
		}
	}

	@Test
	void practiceModeAcceptsPracticeDrillContracts() {
		SimulateResponseDto response = service.simulate(
				"player-practice",
				new SimulateRequestDto(
						"ignored",
						"practice-m1-recruit-l1",
						"practice-panel",
						"chal-practice-m1-l1",
						"GET",
						"/api/practice/doors/drill",
						Map.of(),
						"",
						"PRACTICE"));

		assertTrue(response.correct());
		assertFalse(response.firstAttemptRecorded());
		assertTrue(response.deviceChanges().isEmpty());
	}
}
