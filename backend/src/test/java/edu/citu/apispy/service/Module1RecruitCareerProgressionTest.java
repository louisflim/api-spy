package edu.citu.apispy.service;

import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.MissionProgressDto;
import edu.citu.apispy.dto.SimulateRequestDto;
import edu.citu.apispy.dto.SimulateResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Module 1 Recruit sequential unlock: Level N requires career clear of Level N-1.
 */
class Module1RecruitCareerProgressionTest {

	private MissionProgressService progress;
	private RequestSimulationService simulate;
	private ContractCatalogService contracts;

	@BeforeEach
	void setUp() {
		ObjectMapper objectMapper = new ObjectMapper();
		ContentLoaderBundles bundle = ContentLoaderBundles.create(objectMapper, true);
		progress = bundle.missionProgress();
		simulate = bundle.service();
		contracts = bundle.contracts();
	}

	@Test
	void levelOneIsAlwaysAccessible() {
		assertDoesNotThrow(() -> progress.getOrCreate("career-l1", "m1-recruit-l1"));
		assertTrue(progress.isRecruitLevelUnlocked("career-l1", 1));
	}

	@Test
	void levelTwoRequiresLevelOneCareerClear() {
		IllegalStateException locked = assertThrows(
				IllegalStateException.class,
				() -> progress.getOrCreate("career-l2", "m1-recruit-l2"));
		assertTrue(locked.getMessage().contains("m1-recruit-l1"));

		clearMission("career-l2", "m1-recruit-l1");
		MissionProgressDto opened = progress.getOrCreate("career-l2", "m1-recruit-l2");
		assertEquals("m1-recruit-l2", opened.missionId());
	}

	@Test
	void levelSevenRequiresLevelSixCareerClear() {
		IllegalStateException locked = assertThrows(
				IllegalStateException.class,
				() -> progress.getOrCreate("career-l7", "m1-recruit-l7"));
		assertTrue(locked.getMessage().contains("m1-recruit-l6"));

		for (int level = 1; level <= 6; level++) {
			clearMission("career-l7", "m1-recruit-l" + level);
		}
		MissionProgressDto boss = progress.getOrCreate("career-l7", "m1-recruit-l7");
		assertEquals("m1-recruit-l7", boss.missionId());
		assertEquals(5, boss.requiredDeviceCount());
		assertTrue(boss.finalLockRequired());
	}

	@Test
	void fieldAgentUnlocksAfterRecruitFinalRoomAndSpecialAfterFieldFinalRoom() {
		String agent = "career-ranks";
		IllegalStateException fieldLocked = assertThrows(
				IllegalStateException.class,
				() -> progress.getOrCreate(agent, "m1-field-l1"));
		assertTrue(fieldLocked.getMessage().contains("m1-recruit-l7"));

		for (int level = 1; level <= 7; level++) {
			clearMission(agent, "m1-recruit-l" + level);
		}
		assertEquals("m1-field-l1", progress.getOrCreate(agent, "m1-field-l1").missionId());
		IllegalStateException fieldL2Locked = assertThrows(
				IllegalStateException.class,
				() -> progress.getOrCreate(agent, "m1-field-l2"));
		assertTrue(fieldL2Locked.getMessage().contains("m1-field-l1"));
		IllegalStateException specialLocked = assertThrows(
				IllegalStateException.class,
				() -> progress.getOrCreate(agent, "m1-special-l1"));
		assertTrue(specialLocked.getMessage().contains("m1-field-l7"));

		for (int level = 1; level <= 7; level++) {
			clearMission(agent, "m1-field-l" + level);
		}
		assertEquals("m1-special-l1", progress.getOrCreate(agent, "m1-special-l1").missionId());
		// Promotion never removes access to lower ranks.
		assertDoesNotThrow(() -> progress.startReplay(agent, "m1-recruit-l3"));
	}

	@Test
	void priorCareerMissionFollowsTheProposalProgressionTable() {
		assertEquals(null, MissionProgressService.priorCareerMissionId(1, edu.citu.apispy.domain.mission.Rank.RECRUIT, 1));
		assertEquals("m1-recruit-l7", MissionProgressService.priorCareerMissionId(1, edu.citu.apispy.domain.mission.Rank.FIELD_AGENT, 1));
		assertEquals("m1-field-l7", MissionProgressService.priorCareerMissionId(1, edu.citu.apispy.domain.mission.Rank.SPECIAL_AGENT, 1));
		assertEquals("m1-special-l4", MissionProgressService.priorCareerMissionId(1, edu.citu.apispy.domain.mission.Rank.SPECIAL_AGENT, 5));
		assertEquals("m1-field-l7", MissionProgressService.priorCareerMissionId(2, edu.citu.apispy.domain.mission.Rank.FIELD_AGENT, 1));
	}

	private void clearMission(String participantCode, String missionId) {
		SimulateResponseDto unlock = simulate.simulate(
				participantCode, correctSelectedPrimary(participantCode, missionId));
		assertTrue(unlock.correct());
		// Boss / escalated rooms may require multiple devices — unlock every required device.
		MissionProgressDto current = unlock.missionProgress();
		for (MissionProgressDto.MandatoryDeviceDto device : current.mandatoryDevices()) {
			if (device.unlocked()) {
				continue;
			}
			ApiContract contract = contracts.requireByChallengeId(device.challengeId());
			SimulateResponseDto next = simulate.simulate(
					participantCode,
					request(
							missionId,
							contract.getDeviceId(),
							contract.getChallengeId(),
							contract.getExpectedMethod(),
							resolvePath(contract)));
			assertTrue(next.correct(), "failed to unlock " + device.deviceId());
			current = next.missionProgress();
		}
		if (current.boss() != null) {
			current = progress.reportBossDefeated(participantCode, missionId, current.roomGeneration());
		}
		MissionProgressDto completed = progress.completeAfterExit(
				participantCode, missionId, current.roomGeneration());
		assertTrue(completed.completed());
	}

	private SimulateRequestDto correctSelectedPrimary(String participantCode, String missionId) {
		MissionProgressDto initial = progress.getOrCreate(participantCode, missionId);
		MissionProgressDto.MandatoryDeviceDto selected = initial.mandatoryDevices().stream()
				.filter(d -> !d.finalLock())
				.findFirst()
				.orElse(initial.mandatoryDevices().getFirst());
		ApiContract contract = contracts.requireByChallengeId(selected.challengeId());
		return request(
				missionId,
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

	/** Builds SimulationTestFixtures-like bundle with sequential unlock enabled. */
	private record ContentLoaderBundles(
			RequestSimulationService service,
			MissionProgressService missionProgress,
			ContractCatalogService contracts) {
		static ContentLoaderBundles create(ObjectMapper objectMapper, boolean sequentialUnlock) {
			SimulationTestFixtures.Bundle base = SimulationTestFixtures.create();
			// Rebuild mission progress + simulate with sequential flag when needed.
			edu.citu.apispy.content.ContentLoader contentLoader =
					new edu.citu.apispy.content.ContentLoader(objectMapper);
			ContractCatalogService contracts = new ContractCatalogService(contentLoader);
			MissionCatalogService missions = new MissionCatalogService(contentLoader);
			DeviceEscalationService escalation = new DeviceEscalationService(contentLoader, contracts);
			FirstAttemptService firstAttempts = FirstAttemptService.inMemory();
			ConceptProgressService conceptProgress = ConceptProgressService.inMemory();
			ConceptDecisionAnalyzer analyzer = new ConceptDecisionAnalyzer();
			ConceptDecisionRecorder recorder = ConceptDecisionRecorder.inMemory();
			TerminalVerificationService tvp = TerminalVerificationService.inMemory(contracts, escalation);
			ConceptReviewService reviews = ConceptReviewService.inMemory(contracts);
			edu.citu.apispy.config.AdaptiveLearningProperties properties =
					new edu.citu.apispy.config.AdaptiveLearningProperties();
			AdaptivePracticeService adaptive = AdaptivePracticeService.inMemory(
					properties, firstAttempts, missions, contracts, reviews);
			ScenarioSelectionService scenarioSelection = new ScenarioSelectionService(escalation, contracts);
			ConceptConsistencyService consistency = ConceptConsistencyService.inMemory(
					contracts, conceptProgress, reviews, objectMapper);
			MissionProgressService missionProgress = MissionProgressService.inMemory(
					missions,
					escalation,
					adaptive,
					scenarioSelection,
					contracts,
					tvp,
					consistency,
					objectMapper,
					sequentialUnlock);
			RequestSimulationService service = new RequestSimulationService(
					contracts,
					missions,
					new edu.citu.apispy.validation.SimulatedRequestValidator(java.util.List.of(
							new edu.citu.apispy.validation.MethodValidator(),
							new edu.citu.apispy.validation.PathValidator(),
							new edu.citu.apispy.validation.PathParameterValidator(),
							new edu.citu.apispy.validation.QueryParameterValidator())),
					firstAttempts,
					conceptProgress,
					analyzer,
					recorder,
					tvp,
					new AgentFeedbackService(),
					adaptive,
					reviews,
					PracticeOutcomeService.inMemory(),
					missionProgress,
					escalation,
					scenarioSelection,
					consistency);
			return new ContentLoaderBundles(service, missionProgress, contracts);
		}
	}
}
