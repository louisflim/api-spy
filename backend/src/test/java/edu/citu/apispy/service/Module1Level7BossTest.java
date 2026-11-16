package edu.citu.apispy.service;

import edu.citu.apispy.domain.mission.Rank;
import edu.citu.apispy.domain.mission.RoomResetReason;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.MissionProgressDto;
import edu.citu.apispy.dto.SimulateRequestDto;
import edu.citu.apispy.dto.SimulateResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Module 1 Recruit Level 7 boss: multi-method command-nexus finale.
 */
class Module1Level7BossTest {

	private static final String MISSION = "m1-recruit-l7";

	private ContractCatalogService contracts;
	private RequestSimulationService simulate;
	private MissionProgressService progress;
	private MissionCatalogService catalog;
	private DeviceEscalationService escalation;

	@BeforeEach
	void setUp() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		contracts = bundle.contracts();
		simulate = bundle.service();
		progress = bundle.missionProgress();
		catalog = bundle.missions();
		escalation = bundle.escalation();
	}

	@Test
	void levelSevenIsPlayableAfterLevelSixWithBossContractsAndEscalation() {
		assertTrue(catalog.requirePlayable("m1-recruit-l6").isPlayable());
		assertTrue(catalog.requirePlayable(MISSION).isPlayable());
		assertEquals(7, catalog.requirePlayable(MISSION).getLevel());
		assertEquals(Rank.RECRUIT, catalog.requirePlayable(MISSION).getRank());
		assertFalse(contracts.findByMissionId(MISSION).isEmpty());
		assertTrue(escalation.find(MISSION).isPresent());
		assertEquals("chal-m1-final-lock-l7", escalation.find(MISSION).orElseThrow().finalLockChallengeId());
		// Global curve unchanged for non-boss missions.
		assertEquals(1, escalation.mandatoryDeviceCount(0));
		assertEquals(3, escalation.mandatoryDeviceCount(1));
		assertEquals(5, escalation.mandatoryDeviceCount(2));
		assertFalse(escalation.finalLockRequired(0));
		assertTrue(escalation.finalLockRequired(1));
		// Boss-specific: first attempt already requires full concept coverage + vault seal.
		assertEquals(5, escalation.mandatoryDeviceCount(MISSION, 0));
		assertTrue(escalation.finalLockRequired(MISSION, 0));
		MissionProgressDto initial = progress.getOrCreate("boss-tier0", MISSION);
		assertEquals(5, initial.requiredDeviceCount());
		assertTrue(initial.finalLockRequired());
		assertEquals(6, initial.requiredDeviceIds().size());
		assertTrue(initial.requiredDeviceIds().contains("final-lock-l7"));
	}

	@Test
	void bossDevicesCoverGetPostPutDeleteAndParameters() {
		assertTrue(hasMethod("GET"));
		assertTrue(hasMethod("POST"));
		assertTrue(hasMethod("PUT"));
		assertTrue(hasMethod("DELETE"));
		assertTrue(contracts.requireByChallengeId("chal-m1-boss-circuit-v1").getPathTemplate().contains("{"));
		assertFalse(contracts.requireByChallengeId("chal-m1-boss-alarms-v1").getQueryParameters().isEmpty());
	}

	@Test
	void wrongMethodKeepsSameScenarioWithControlFeedbackAndNoPracticeDoors() {
		MissionProgressDto.MandatoryDeviceDto selected = progress.getOrCreate("boss-wrong", MISSION)
				.mandatoryDevices()
				.getFirst();
		ApiContract contract = contracts.requireByChallengeId(selected.challengeId());
		String wrongMethod = "GET".equals(contract.getExpectedMethod()) ? "POST" : "GET";
		SimulateResponseDto miss = simulate.simulate(
				"boss-wrong",
				request(MISSION, contract.getDeviceId(), contract.getChallengeId(), wrongMethod, resolvePath(contract)));
		assertFalse(miss.correct());
		assertEquals(contract.getChallengeId(), miss.challengeId());
		assertTrue(miss.adaptivePracticeDoors().isEmpty());
		assertTrue(miss.autoPracticeOffer() == null);
		assertTrue(miss.controlFeedback().messages().stream()
				.anyMatch(m -> m.toLowerCase().contains("same challenge") || m.toLowerCase().contains("http")));
	}

	@Test
	void correctSelectedPrimaryUnlocksDeviceWithoutPracticeDoors() {
		SimulateResponseDto ok = simulate.simulate("boss-ok", correctSelectedPrimary("boss-ok"));
		assertTrue(ok.correct());
		assertFalse(ok.missionProgress().unlockedDeviceIds().isEmpty());
		assertTrue(ok.adaptivePracticeDoors().isEmpty());
		assertTrue(ok.missionProgress().adaptivePracticeDoors().isEmpty());
	}

	@Test
	void multiMethodContractsAcceptCorrectRequestsAndRejectWrongOnes() {
		assertMethodRoundTrip(
				"chal-m1-boss-status-v1",
				"nexus-status-board",
				"GET",
				"/api/nexus/status",
				"POST");
		assertMethodRoundTrip(
				"chal-m1-boss-circuit-v1",
				"nexus-circuit-core",
				"GET",
				"/api/nexus/circuits/core-loop",
				"DELETE");
		assertMethodRoundTrip(
				"chal-m1-boss-alarms-v1",
				"nexus-alarm-console",
				"GET",
				"/api/nexus/alarms?zone=core",
				"PUT");
		assertMethodRoundTrip(
				"chal-m1-boss-breach-v1",
				"nexus-breach-desk",
				"POST",
				"/api/nexus/breach-reports/core",
				"GET");
		assertMethodRoundTrip(
				"chal-m1-boss-lockdown-v1",
				"nexus-lockdown-alpha",
				"PUT",
				"/api/nexus/lockdowns/alpha-wing",
				"POST");
		assertMethodRoundTrip(
				"chal-m1-final-lock-l7",
				"final-lock-l7",
				"DELETE",
				"/api/final-locks/nexus",
				"GET");
	}

	@Test
	void apiDrivenResetKeepsBossFullCoverageWithoutChangingGlobalTierCurve() {
		MissionProgressDto initial = progress.getOrCreate("boss-esc", MISSION);
		assertEquals(5, initial.requiredDeviceCount());
		assertEquals(6, initial.requiredDeviceIds().size());
		MissionProgressDto.MandatoryDeviceDto selected = initial.mandatoryDevices().stream()
				.filter(d -> !d.finalLock())
				.findFirst()
				.orElseThrow();
		ApiContract contract = contracts.requireByChallengeId(selected.challengeId());
		simulate.simulate(
				"boss-esc",
				request(
						MISSION,
						contract.getDeviceId(),
						contract.getChallengeId(),
						"GET".equals(contract.getExpectedMethod()) ? "POST" : "GET",
						"/api/nexus/wrong"));
		MissionProgressDto escalated = progress.resetRoom(
				"boss-esc",
				MISSION,
				RoomResetReason.PLAYER,
				initial.roomGeneration());
		assertEquals(5, escalated.requiredDeviceCount());
		assertTrue(escalated.finalLockRequired());
		assertTrue(escalated.requiredDeviceIds().contains("final-lock-l7"));
		assertTrue(escalated.adaptivePracticeDoors().isEmpty());
		assertEquals(6, escalated.requiredDeviceIds().size());
		// Global curve for L1 remains 1 → 3 → 5.
		assertEquals(1, escalation.mandatoryDeviceCount("m1-recruit-l1", 0));
		assertEquals(3, escalation.mandatoryDeviceCount("m1-recruit-l1", 1));
	}

	@Test
	void completeAndReplayPreserveCareerClear() {
		MissionProgressDto current = unlockAllRequired("boss-clear");
		MissionProgressDto completed = progress.completeAfterExit(
				"boss-clear", MISSION, current.roomGeneration());
		assertTrue(completed.completed());
		assertTrue(completed.replayable());

		MissionProgressDto replay = progress.startReplay("boss-clear", MISSION);
		assertTrue(replay.completed());
		assertTrue(replay.unlockedDeviceIds().isEmpty());
		assertTrue(replay.replayActive());
		assertEquals("REPLAY", replay.activeRunKind());
		assertEquals(5, replay.requiredDeviceCount());
		assertTrue(replay.finalLockRequired());
	}

	private MissionProgressDto unlockAllRequired(String participantCode) {
		MissionProgressDto current = progress.getOrCreate(participantCode, MISSION);
		for (MissionProgressDto.MandatoryDeviceDto device : current.mandatoryDevices()) {
			if (device.unlocked()) {
				continue;
			}
			ApiContract contract = contracts.requireByChallengeId(device.challengeId());
			SimulateResponseDto ok = simulate.simulate(
					participantCode,
					request(
							MISSION,
							contract.getDeviceId(),
							contract.getChallengeId(),
							contract.getExpectedMethod(),
							resolvePath(contract)));
			assertTrue(ok.correct(), "failed unlocking " + device.deviceId());
			current = ok.missionProgress();
		}
		assertTrue(current.exitReady());
		return current;
	}

	@Test
	void gradedBossBriefsDoNotPasteCompleteAnswers() {
		for (String challengeId : List.of(
				"chal-m1-boss-status-v1",
				"chal-m1-boss-circuit-v1",
				"chal-m1-boss-alarms-v1",
				"chal-m1-boss-breach-v1",
				"chal-m1-boss-lockdown-v1",
				"chal-m1-final-lock-l7")) {
			ApiContract contract = contracts.requireByChallengeId(challengeId);
			assertFalse(contract.getDocumentation().contains(
					contract.getExpectedMethod() + " " + resolvePath(contract)));
		}
	}

	private boolean hasMethod(String method) {
		return contracts.findByMissionId(MISSION).stream()
				.anyMatch(c -> method.equals(c.getExpectedMethod()) && !c.getDeviceId().startsWith("practice-"));
	}

	private void assertMethodRoundTrip(
			String challengeId,
			String deviceId,
			String correctMethod,
			String correctPath,
			String wrongMethod) {
		SimulateResponseDto wrong = simulate.simulate(
				"boss-methods",
				request(MISSION, deviceId, challengeId, wrongMethod, correctPath));
		assertFalse(wrong.correct(), challengeId + " should reject " + wrongMethod);
		SimulateResponseDto ok = simulate.simulate(
				"boss-methods-" + challengeId,
				request(MISSION, deviceId, challengeId, correctMethod, correctPath));
		assertTrue(ok.correct(), challengeId + " should accept " + correctMethod);
		assertTrue(ok.adaptivePracticeDoors().isEmpty());
	}

	private SimulateRequestDto correctSelectedPrimary(String participantCode) {
		MissionProgressDto initial = progress.getOrCreate(participantCode, MISSION);
		MissionProgressDto.MandatoryDeviceDto selected = initial.mandatoryDevices().getFirst();
		ApiContract contract = contracts.requireByChallengeId(selected.challengeId());
		return request(
				MISSION,
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
