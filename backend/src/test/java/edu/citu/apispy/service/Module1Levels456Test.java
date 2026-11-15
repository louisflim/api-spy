package edu.citu.apispy.service;

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
 * Module 1 Recruit Levels 4–6: catalog presence, method/endpoint validation, unlock, and replay.
 */
class Module1Levels456Test {

	private ContractCatalogService contracts;
	private RequestSimulationService simulate;
	private MissionProgressService progress;
	private MissionCatalogService catalog;

	@BeforeEach
	void setUp() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		contracts = bundle.contracts();
		simulate = bundle.service();
		progress = bundle.missionProgress();
		catalog = bundle.missions();
	}

	@Test
	void levelsFourThroughSixArePlayableWithContracts() {
		assertTrue(catalog.requirePlayable("m1-recruit-l4").isPlayable());
		assertTrue(catalog.requirePlayable("m1-recruit-l5").isPlayable());
		assertTrue(catalog.requirePlayable("m1-recruit-l6").isPlayable());
		assertFalse(contracts.findByMissionId("m1-recruit-l4").isEmpty());
		assertFalse(contracts.findByMissionId("m1-recruit-l5").isEmpty());
		assertFalse(contracts.findByMissionId("m1-recruit-l6").isEmpty());
	}

	@Test
	void levelFourPostAcceptsCorrectRegistrationAndRejectsWrongMethod() {
		SimulateResponseDto wrong = simulate.simulate(
				"agent-l4",
				request("m1-recruit-l4", "checkpoint-alpha", "chal-m1-post-registration-v1", "GET", "/api/registrations/alpha"));
		assertFalse(wrong.correct());
		assertTrue(wrong.errorCodes().contains("METHOD"));

		SimulateResponseDto ok = simulate.simulate(
				"agent-l4",
				request("m1-recruit-l4", "checkpoint-alpha", "chal-m1-post-registration-v1", "POST", "/api/registrations/alpha"));
		assertTrue(ok.correct());
		assertEquals(201, ok.simulatedResponse().status());
		assertTrue(ok.missionProgress().unlockedDeviceIds().contains("checkpoint-alpha"));
	}

	@Test
	void levelFivePutRequiresMethodPathAndIdentifier() {
		SimulateResponseDto wrongMethod = simulate.simulate(
				"agent-l5",
				request("m1-recruit-l5", "clearance-north", "chal-m1-put-clearance-v1", "GET", "/api/clearances/north-desk"));
		assertFalse(wrongMethod.correct());
		assertTrue(wrongMethod.errorCodes().contains("METHOD"));

		SimulateResponseDto wrongId = simulate.simulate(
				"agent-l5",
				request("m1-recruit-l5", "clearance-north", "chal-m1-put-clearance-v1", "PUT", "/api/clearances/wrong-desk"));
		assertFalse(wrongId.correct());

		SimulateResponseDto ok = simulate.simulate(
				"agent-l5",
				request("m1-recruit-l5", "clearance-north", "chal-m1-put-clearance-v1", "PUT", "/api/clearances/north-desk"));
		assertTrue(ok.correct());
		assertEquals(200, ok.simulatedResponse().status());
		assertTrue(ok.missionProgress().unlockedDeviceIds().contains("clearance-north"));
	}

	@Test
	void levelSixDeleteDeactivatesBadgeAndRejectsWrongEndpoint() {
		SimulateResponseDto wrongPath = simulate.simulate(
				"agent-l6",
				request("m1-recruit-l6", "badge-red", "chal-m1-delete-badge-v1", "DELETE", "/api/access-badges/wrong-pass"));
		assertFalse(wrongPath.correct());

		SimulateResponseDto ok = simulate.simulate(
				"agent-l6",
				request("m1-recruit-l6", "badge-red", "chal-m1-delete-badge-v1", "DELETE", "/api/access-badges/red-pass"));
		assertTrue(ok.correct());
		assertEquals(200, ok.simulatedResponse().status());
		assertTrue(ok.missionProgress().unlockedDeviceIds().contains("badge-red"));
	}

	@Test
	void levelFourThroughSixCompleteAndReplayPreserveCareerClear() {
		for (Case row : List.of(
				new Case("m1-recruit-l4", "checkpoint-alpha", "chal-m1-post-registration-v1", "POST", "/api/registrations/alpha"),
				new Case("m1-recruit-l5", "clearance-north", "chal-m1-put-clearance-v1", "PUT", "/api/clearances/north-desk"),
				new Case("m1-recruit-l6", "badge-red", "chal-m1-delete-badge-v1", "DELETE", "/api/access-badges/red-pass"))) {
			String agent = "clear-" + row.missionId();
			SimulateResponseDto unlock = simulate.simulate(
					agent, request(row.missionId(), row.deviceId(), row.challengeId(), row.method(), row.path()));
			MissionProgressDto completed = progress.completeAfterExit(
					agent, row.missionId(), unlock.missionProgress().roomGeneration());
			assertTrue(completed.completed());
			assertTrue(completed.replayable());

			MissionProgressDto replay = progress.startReplay(agent, row.missionId());
			assertTrue(replay.completed(), "career completion must remain");
			assertTrue(replay.unlockedDeviceIds().isEmpty());
			assertTrue(replay.replayActive());
			assertFalse(replay.replayable());
			assertEquals("REPLAY", replay.activeRunKind());
			assertTrue(completed.completedAt() != null && !completed.completedAt().isBlank());
		}
	}

	@Test
	void gradedContractsDoNotExposeExactAnswerAsSingleLine() {
		for (String missionId : List.of("m1-recruit-l4", "m1-recruit-l5", "m1-recruit-l6")) {
			ApiContract primary = contracts.requireByChallengeId(
					catalog.requirePlayable(missionId).getPrimaryChallengeId());
			String docs = primary.getDocumentation();
			assertFalse(docs.contains(primary.getExpectedMethod() + " " + resolvePath(primary)));
		}
	}

	private static String resolvePath(ApiContract contract) {
		String path = contract.getPathTemplate();
		for (Map.Entry<String, String> entry : contract.getPathParameters().entrySet()) {
			path = path.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return path;
	}

	private static SimulateRequestDto request(
			String missionId, String deviceId, String challengeId, String method, String path) {
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

	private record Case(String missionId, String deviceId, String challengeId, String method, String path) {
	}
}
