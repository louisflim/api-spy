package edu.citu.apispy.service;

import edu.citu.apispy.config.AdaptiveLearningProperties;
import edu.citu.apispy.dto.AdaptivePracticeDoorDto;
import edu.citu.apispy.dto.SimulateRequestDto;
import edu.citu.apispy.dto.SimulateResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveLearningServiceTest {

	private RequestSimulationService service;
	private AdaptiveLearningProperties properties;

	@BeforeEach
	void setUp() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		service = bundle.service();
		properties = new AdaptiveLearningProperties();
	}

	@Test
	void incorrectAttemptThresholdRemainsTwo() {
		assertEquals(2, properties.getIncorrectAttemptThreshold());
		assertEquals(2, properties.getMaxPracticeDoorsPerRoom());
	}

	@Test
	void singleEligibleFailureDoesNotOpenAdaptivePracticeDoor() {
		SimulateResponseDto onlyOne = service.simulate("agent-one", mission(
				"m1-recruit-l3",
				"sensor-panel",
				"chal-m1-query-sensor-v1",
				"POST",
				"/api/sensors"));
		assertFalse(onlyOne.correct());
		assertTrue(onlyOne.firstAttemptRecorded());
		assertTrue(
				onlyOne.adaptivePracticeDoors().isEmpty(),
				"threshold is 2 distinct eligible failures; one miss must not open a door");
	}

	@Test
	void twoDistinctMandatoryFailuresAcrossRoomsTriggerAdaptivePractice() {
		SimulateResponseDto room1 = service.simulate("agent-cross", mission(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"GET",
				"/api/doors/wrong"));
		assertFalse(room1.correct());
		assertTrue(room1.adaptivePracticeDoors().isEmpty());

		SimulateResponseDto room2 = service.simulate("agent-cross", mission(
				"m1-recruit-l2",
				"lock-north",
				"chal-m1-path-lock-v1",
				"GET",
				"/api/doors/alpha"));
		assertFalse(room2.correct());
		assertTrue(room2.errorCodes().contains("ENDPOINT"));
		assertFalse(room2.adaptivePracticeDoors().isEmpty());
		assertTrue(room2.adaptivePracticeDoors().stream()
				.anyMatch(door -> "http.endpoint".equals(door.conceptTag())));
		assertTrue(room2.adaptivePracticeDoors().stream()
				.anyMatch(door -> "chal-practice-m1-l1".equals(door.challengeId())));
		assertTrue(room2.adaptivePracticeDoors().size() <= 2);
	}

	@Test
	void previousPracticeTriggerEvidenceDoesNotGenerateDuplicateDoors() {
		service.simulate("agent-dup", mission(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"GET",
				"/api/doors/wrong"));
		SimulateResponseDto firstTrigger = service.simulate("agent-dup", mission(
				"m1-recruit-l2",
				"lock-north",
				"chal-m1-path-lock-v1",
				"GET",
				"/api/doors/alpha"));
		assertFalse(firstTrigger.adaptivePracticeDoors().isEmpty());
		Set<String> firstKeys = firstTrigger.adaptivePracticeDoors().stream()
				.map(door -> door.conceptTag() + "|" + door.challengeId())
				.collect(Collectors.toSet());

		SimulateResponseDto again = service.simulate("agent-dup", mission(
				"m1-recruit-l2",
				"lock-north",
				"chal-m1-path-lock-v1",
				"POST",
				"/api/locks/north-gate"));
		assertFalse(again.firstAttemptRecorded());
		Set<String> againKeys = again.adaptivePracticeDoors().stream()
				.map(door -> door.conceptTag() + "|" + door.challengeId())
				.collect(Collectors.toSet());
		assertEquals(firstKeys, againKeys);
	}

	@Test
	void progressEndpointSurfacesActivePracticeDoorsForRoom() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		RequestSimulationService sim = bundle.service();
		MissionProgressService progress = bundle.missionProgress();

		sim.simulate("agent-reload", mission(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"GET",
				"/api/doors/wrong"));
		sim.simulate("agent-reload", mission(
				"m1-recruit-l2",
				"lock-north",
				"chal-m1-path-lock-v1",
				"GET",
				"/api/doors/alpha"));

		var roomProgress = progress.getOrCreate("agent-reload", "m1-recruit-l2");
		assertFalse(roomProgress.adaptivePracticeDoors().isEmpty());
		assertTrue(roomProgress.adaptivePracticeDoors().stream()
				.anyMatch(door -> "http.endpoint".equals(door.conceptTag())));
		assertTrue(roomProgress.adaptivePracticeDoors().size() <= 2);
	}

	@Test
	void adaptivePracticeDoesNotUnlockMandatoryExit() {
		service.simulate("agent-practice-exit", mission(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"GET",
				"/api/doors/wrong"));
		SimulateResponseDto triggered = service.simulate("agent-practice-exit", mission(
				"m1-recruit-l2",
				"lock-north",
				"chal-m1-path-lock-v1",
				"GET",
				"/api/doors/alpha"));
		AdaptivePracticeDoorDto door = triggered.adaptivePracticeDoors().getFirst();

		SimulateResponseDto practiceOk = service.simulate(
				"agent-practice-exit",
				new SimulateRequestDto(
						"agent-practice-exit",
						door.practiceMissionId(),
						door.deviceId(),
						door.challengeId(),
						door.expectedMethod(),
						door.pathTemplate().contains("{")
								? "/api/practice/locks/drill-bay"
								: door.pathTemplate(),
						Map.of(),
						"",
						"PRACTICE"));
		assertTrue(practiceOk.correct());
		assertTrue(practiceOk.deviceChanges().isEmpty());
		assertFalse(practiceOk.missionProgress() != null && practiceOk.missionProgress().exitReady());
	}

	@Test
	void retriesOfOneChallengeDoNotCountAsMultipleIndependentFailures() {
		service.simulate("agent-retry", mission(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"POST",
				"/api/doors/alpha"));
		SimulateResponseDto secondFail = service.simulate("agent-retry", mission(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"PUT",
				"/api/doors/alpha"));
		assertFalse(secondFail.firstAttemptRecorded());
		assertTrue(secondFail.adaptivePracticeDoors().isEmpty());
	}

	@Test
	void forgedPracticeModeCannotBypassMandatoryMissionEvaluation() {
		assertThrows(IllegalArgumentException.class, () -> service.simulate(
				"forger",
				new SimulateRequestDto(
						"forger",
						"m1-recruit-l1",
						"door-alpha",
						"chal-m1-get-door-v1",
						"GET",
						"/api/doors/alpha",
						Map.of(),
						"",
						"PRACTICE")));
	}

	@Test
	void authorizedPracticeDoesNotRecordFirstAttemptOrUnlockMissionDevices() {
		SimulateResponseDto response = service.simulate(
				"practice-player",
				new SimulateRequestDto(
						"practice-player",
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
		assertFalse(response.heartsChanged());
	}

	private static SimulateRequestDto mission(
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
