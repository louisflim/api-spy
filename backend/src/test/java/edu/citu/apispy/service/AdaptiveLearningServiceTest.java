package edu.citu.apispy.service;

import edu.citu.apispy.config.AdaptiveLearningProperties;
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
	void incorrectAttemptThresholdRemainsTwoForHistoricalReviewEnqueue() {
		assertEquals(2, properties.getIncorrectAttemptThreshold());
		assertEquals(2, properties.getMaxPracticeDoorsPerRoom());
	}

	@Test
	void singleEligibleFailureDoesNotSpawnAdaptivePracticeDoor() {
		SimulateResponseDto onlyOne = service.simulate("agent-one", mission(
				"m1-recruit-l3",
				"sensor-panel",
				"chal-m1-query-sensor-v1",
				"POST",
				"/api/sensors"));
		assertFalse(onlyOne.correct());
		assertTrue(onlyOne.firstAttemptRecorded());
		assertTrue(onlyOne.adaptivePracticeDoors().isEmpty());
		assertNull(onlyOne.autoPracticeOffer());
	}

	@Test
	void repeatedConceptMissesNeverSpawnPhysicalPracticeDoors() {
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
		assertTrue(
				room2.adaptivePracticeDoors().isEmpty(),
				"physical adaptive practice doors are retired even after recurring difficulty");
		assertNull(room2.autoPracticeOffer());
	}

	@Test
	void sameRoomVerificationSequenceKeepsDoorsEmptyAndStaysOnWrongScenario() {
		SimulateResponseDto scenario1Wrong = service.simulate("agent-seq", mission(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"POST",
				"/api/doors/alpha"));
		assertFalse(scenario1Wrong.correct());
		assertTrue(scenario1Wrong.firstAttemptRecorded());
		assertEquals("chal-m1-get-door-v1", scenario1Wrong.challengeId());
		assertTrue(scenario1Wrong.adaptivePracticeDoors().isEmpty());
		assertTrue(Boolean.FALSE.equals(
				scenario1Wrong.firstAttemptRecord().conceptFirstAttemptCorrect().get("http.method.get")));

		SimulateResponseDto scenario1Fix = service.simulate("agent-seq", mission(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"GET",
				"/api/doors/alpha"));
		assertTrue(scenario1Fix.correct());
		assertFalse(scenario1Fix.firstAttemptRecorded());
		assertNotNull(scenario1Fix.terminalVerification());
		assertNotNull(scenario1Fix.terminalVerification().verifyAnotherScenario());
		var next = scenario1Fix.terminalVerification().verifyAnotherScenario();
		assertNotEquals("chal-m1-get-door-v1", next.challengeId());

		SimulateResponseDto scenario2Wrong = service.simulate("agent-seq", mission(
				"m1-recruit-l1",
				next.deviceId(),
				next.challengeId(),
				"DELETE",
				"/api/doors/bravo"));
		assertFalse(scenario2Wrong.correct());
		assertTrue(scenario2Wrong.firstAttemptRecorded());
		assertTrue(scenario2Wrong.errorCodes().contains("METHOD"));
		assertEquals(next.challengeId(), scenario2Wrong.challengeId());
		assertTrue(scenario2Wrong.adaptivePracticeDoors().isEmpty());
		assertNull(scenario2Wrong.autoPracticeOffer());
	}

	@Test
	void progressEndpointNeverSurfacesActivePracticeDoors() {
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
		assertTrue(roomProgress.adaptivePracticeDoors().isEmpty());
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
	void wrongGradedSubmissionStaysOnScenarioWithControlFeedbackAndNoAutoPractice() {
		SimulateResponseDto miss = service.simulate("agent-inline", mission(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"POST",
				"/api/doors/alpha"));
		assertFalse(miss.correct());
		assertTrue(miss.firstAttemptRecorded());
		assertEquals("chal-m1-get-door-v1", miss.challengeId());
		assertTrue(miss.adaptivePracticeDoors().isEmpty());
		assertNull(miss.autoPracticeOffer());
		assertTrue(miss.controlFeedback().messages().stream()
				.anyMatch(m -> m.toLowerCase().contains("http method")));
		assertTrue(miss.controlFeedback().messages().stream()
				.anyMatch(m -> m.toLowerCase().contains("same challenge")));
		assertTrue(miss.controlFeedback().messages().stream()
				.noneMatch(m -> m.toLowerCase().contains("ungraded practice")));
		assertTrue(miss.controlFeedback().messages().stream()
				.noneMatch(m -> m.toLowerCase().contains("practice terminal")));
	}

	@Test
	void priorCorrectFirstAttemptIsPreservedAndDoesNotMintDoorsOnLaterWrongRetry() {
		SimulateResponseDto priorS1 = service.simulate("agent-history", mission(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"GET",
				"/api/doors/alpha"));
		assertTrue(priorS1.correct());
		assertTrue(priorS1.firstAttemptRecorded());

		SimulateResponseDto priorS2 = service.simulate("agent-history", mission(
				"m1-recruit-l1",
				"door-bravo",
				"chal-m1-get-door-v2",
				"GET",
				"/api/doors/bravo"));
		assertTrue(priorS2.correct());
		assertTrue(priorS2.firstAttemptRecorded());
		assertTrue(priorS2.firstAttemptRecord().firstAttemptCorrect());
		assertEquals("chal-m1-get-door-v2", priorS2.firstAttemptRecord().challengeId());

		SimulateResponseDto laterWrong = service.simulate("agent-history", mission(
				"m1-recruit-l1",
				"door-bravo",
				"chal-m1-get-door-v2",
				"POST",
				"/api/doors/bravo"));
		assertFalse(laterWrong.correct());
		assertTrue(laterWrong.errorCodes().contains("METHOD"));
		assertFalse(laterWrong.firstAttemptRecorded());
		assertTrue(laterWrong.firstAttemptRecord().firstAttemptCorrect());
		assertEquals("GET", laterWrong.firstAttemptRecord().method());
		assertTrue(Boolean.TRUE.equals(
				laterWrong.firstAttemptRecord().conceptFirstAttemptCorrect().get("http.method.get")));
		assertTrue(laterWrong.adaptivePracticeDoors().isEmpty());
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
	void authorizedMissionTrainingPracticeDoesNotRecordFirstAttemptOrUnlockMissionDevices() {
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
		assertTrue(response.terminalVerification() == null);
		assertTrue(response.adaptivePracticeDoors().isEmpty());
	}

	@Test
	void advancePracticeRoundAndMarkLessonOpenedAreRetired() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		AdaptivePracticeService adaptive = bundle.adaptivePractice();

		assertThrows(
				IllegalStateException.class,
				() -> adaptive.advancePracticeRound("agent-round", "m1-recruit-l2", "http.endpoint"));
		assertThrows(
				IllegalStateException.class,
				() -> adaptive.markLessonOpened("agent-lesson", "m1-recruit-l2", "http.endpoint"));
		assertTrue(adaptive.activeDoorsForRoom("agent-round", "m1-recruit-l2").isEmpty());
		assertTrue(adaptive.supportOffersForRoom("agent-round", "m1-recruit-l2").isEmpty());
	}

	@Test
	void recurringDifficultyEnqueuesLaterReviewWithoutDoors() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		RequestSimulationService sim = bundle.service();
		AdaptivePracticeService adaptive = bundle.adaptivePractice();

		sim.simulate("agent-support", mission(
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				"GET",
				"/api/doors/wrong"));
		SimulateResponseDto triggered = sim.simulate("agent-support", mission(
				"m1-recruit-l2",
				"lock-north",
				"chal-m1-path-lock-v1",
				"GET",
				"/api/doors/alpha"));
		assertTrue(triggered.adaptivePracticeDoors().isEmpty());
		assertNull(triggered.autoPracticeOffer());
		assertTrue(adaptive.activeDoorsForRoom("agent-support", "m1-recruit-l2").isEmpty());
		assertNotNull(triggered.conceptReviewQueue());
		assertTrue(triggered.conceptReviewQueue().stream()
				.anyMatch(item -> "http.endpoint".equals(item.conceptTag())));
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
