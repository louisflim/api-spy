package edu.citu.apispy.service;

import edu.citu.apispy.domain.mission.MissionDefinition;
import edu.citu.apispy.domain.mission.Rank;
import edu.citu.apispy.domain.mission.RoomResetReason;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.MissionProgressDto;
import edu.citu.apispy.dto.SimulateRequestDto;
import edu.citu.apispy.dto.SimulateResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Module 1 Field Agent and Special Agent tracks: catalog, authored content quality,
 * solvability, escalation and the Level 7 boss rules on every rank.
 */
class Module1HigherRanksTest {

	private static final List<String> TRACKS = List.of("field", "special");

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
	void allFourteenHigherRankMissionsArePlayableWithTheRightRank() {
		for (String track : TRACKS) {
			Rank rank = track.equals("field") ? Rank.FIELD_AGENT : Rank.SPECIAL_AGENT;
			for (int level = 1; level <= 7; level++) {
				MissionDefinition mission = catalog.requirePlayable("m1-" + track + "-l" + level);
				assertEquals(rank, mission.getRank());
				assertEquals(level, mission.getLevel());
				assertTrue(escalation.find(mission.getMissionId()).isPresent(), mission.getMissionId());
				assertFalse(contracts.findByMissionId(mission.getMissionId()).isEmpty());
			}
		}
		// Recruit + Field Agent + Special Agent for Module 1.
		assertEquals(21, catalog.playableMissions().stream().filter(m -> m.getModule() == 1).count());
	}

	@Test
	void everyAuthoredScenarioIsSolvableAndUniquelyBound() {
		Set<String> devices = new HashSet<>();
		for (ApiContract contract : higherRankContracts()) {
			assertTrue(devices.add(contract.getDeviceId()), "device ids must be unique: " + contract.getDeviceId());
			SimulateResponseDto ok = simulate.simulate("solve-" + contract.getChallengeId(), correct(contract));
			assertTrue(ok.correct(), "authored answer must solve " + contract.getChallengeId());
		}
	}

	@Test
	void higherRanksAreHarderThanRecruitAtTheSameLevel() {
		for (int level = 1; level <= 6; level++) {
			double recruit = averageParts("m1-recruit-l" + level);
			double field = averageParts("m1-field-l" + level);
			double special = averageParts("m1-special-l" + level);
			assertTrue(field > recruit, "Field Agent L" + level + " should need more request parts than Recruit");
			// Special Agent difficulty comes from derived identifiers and decoy routes (checked below),
			// not raw URL length, so it is only required to exceed Recruit here.
			assertTrue(special > recruit, "Special Agent L" + level + " should need more request parts than Recruit");
		}
		// Special Agent briefs never hand over identifiers directly: every graded scenario
		// requires deriving values from a lookup table / ID format rule, or picking past decoy routes.
		for (ApiContract contract : higherRankContracts()) {
			if (!contract.getMissionId().startsWith("m1-special-") || contract.getDeviceId().startsWith("final-lock-")) {
				continue;
			}
			String docs = contract.getDocumentation();
			assertTrue(docs.contains("would be") || docs.contains("→") || docs.contains("Similar routes"),
					"Special Agent brief must require derivation: " + contract.getChallengeId());
		}
	}

	@Test
	void briefsTeachTheRulesWithoutPastingTheAnswer() {
		for (ApiContract contract : higherRankContracts()) {
			String docs = contract.getDocumentation();
			String answer = resolvePath(contract);
			assertFalse(docs.contains(answer), "brief pastes the answer for " + contract.getChallengeId());
			assertFalse(docs.contains(contract.getExpectedMethod() + " " + contract.getPathTemplate()),
					"brief pastes method+path for " + contract.getChallengeId());
			assertTrue(docs.contains("/api/"), "brief must describe the resource path");
			assertTrue(docs.toLowerCase().contains("construct"), "brief must require independent construction");
		}
	}

	@Test
	void missingQueryParameterOrWrongMethodNeverSolves() {
		for (ApiContract contract : higherRankContracts()) {
			String wrongMethod = "GET".equals(contract.getExpectedMethod()) ? "POST" : "GET";
			SimulateResponseDto method = simulate.simulate("wrong-m-" + contract.getChallengeId(),
					request(contract, wrongMethod, resolvePath(contract)));
			assertFalse(method.correct(), "wrong method solved " + contract.getChallengeId());
			if (!contract.getQueryParameters().isEmpty()) {
				SimulateResponseDto noQuery = simulate.simulate("wrong-q-" + contract.getChallengeId(),
						request(contract, contract.getExpectedMethod(), resolvePath(contract).split("\\?")[0]));
				assertFalse(noQuery.correct(), "missing query solved " + contract.getChallengeId());
			}
		}
	}

	@Test
	void escalationGivesDistinctScenariosAtEveryTier() {
		for (String track : TRACKS) {
			for (int level = 1; level <= 6; level++) {
				String mission = "m1-" + track + "-l" + level;
				String agent = "tier-" + mission;
				MissionProgressDto room = progress.getOrCreate(agent, mission);
				assertEquals(1, room.requiredDeviceCount());
				for (int tier = 1; tier <= 2; tier++) {
					stall(agent, mission, room);
					room = progress.resetRoom(agent, mission, RoomResetReason.API_STALLED);
					assertEquals(tier == 1 ? 3 : 5, room.requiredDeviceCount(), mission);
					assertTrue(room.finalLockRequired());
					List<String> ids = room.mandatoryDevices().stream().map(MissionProgressDto.MandatoryDeviceDto::challengeId).toList();
					assertEquals(ids.size(), new HashSet<>(ids).size(), "duplicate scenario in one room: " + ids);
				}
			}
		}
	}

	@Test
	void levelSevenIsABossFinaleOnEveryRank() {
		for (String track : TRACKS) {
			String mission = "m1-" + track + "-l7";
			assertTrue(escalation.isBossFinale(mission));
			MissionProgressDto boss = progress.getOrCreate("boss-" + track, mission);
			assertEquals(5, boss.requiredDeviceCount());
			assertTrue(boss.finalLockRequired());
			assertTrue(boss.boss() != null && !boss.boss().shieldDown());
			// One concept family per terminal: five different resource templates.
			Set<String> templates = new HashSet<>();
			for (MissionProgressDto.MandatoryDeviceDto device : boss.mandatoryDevices()) {
				if (!device.finalLock()) {
					templates.add(contracts.requireByChallengeId(device.challengeId()).getPathTemplate());
				}
			}
			assertEquals(5, templates.size(), mission);
			for (MissionProgressDto.MandatoryDeviceDto device : boss.mandatoryDevices()) {
				boss = simulate.simulate("boss-" + track,
						correct(contracts.requireByChallengeId(device.challengeId()))).missionProgress();
			}
			assertTrue(boss.boss().shieldDown());
			assertFalse(boss.exitReady(), "boss must still be defeated");
			boss = progress.reportBossDefeated("boss-" + track, mission, boss.roomGeneration());
			assertTrue(progress.completeAfterExit("boss-" + track, mission, boss.roomGeneration()).completed());
		}
		assertFalse(escalation.isBossFinale("m1-field-l6"));
	}

	@Test
	void missingQueryIsBlamedOnTheQueryConceptOnlyAndWrongPathValueOnThePathConceptOnly() {
		ApiContract mixed = contracts.requireByChallengeId("chal-m1-fa-l3-badges-v2");
		String base = resolvePath(mixed).split("\\?")[0];

		Map<String, Boolean> missingQuery = simulate.simulate("attr-query",
				request(mixed, mixed.getExpectedMethod(), base)).firstAttemptRecord().conceptFirstAttemptCorrect();
		assertEquals(Boolean.TRUE, missingQuery.get("http.path_parameter"), "path was right");
		assertEquals(Boolean.FALSE, missingQuery.get("http.query_parameter"), "query was missing");

		String wrongZone = resolvePath(mixed).replace("/armory/", "/vault/");
		Map<String, Boolean> wrongPath = simulate.simulate("attr-path",
				request(mixed, mixed.getExpectedMethod(), wrongZone)).firstAttemptRecord().conceptFirstAttemptCorrect();
		assertEquals(Boolean.FALSE, wrongPath.get("http.path_parameter"), "path value was wrong");
		assertEquals(Boolean.TRUE, wrongPath.get("http.query_parameter"), "query was right");
	}

	// ---- helpers -------------------------------------------------------------------

	private List<ApiContract> higherRankContracts() {
		return contracts.allContracts().stream()
				.filter(c -> c.getMissionId().startsWith("m1-field-") || c.getMissionId().startsWith("m1-special-"))
				.toList();
	}

	/** Path params + query params a learner must supply (excludes final locks). */
	private double averageParts(String missionId) {
		return contracts.findByMissionId(missionId).stream()
				.filter(c -> !c.getDeviceId().startsWith("final-lock-"))
				.mapToInt(c -> c.getPathParameters().size() + c.getQueryParameters().size()
						+ c.getPathTemplate().split("/").length)
				.average()
				.orElse(0);
	}

	private void stall(String agent, String mission, MissionProgressDto room) {
		MissionProgressDto.MandatoryDeviceDto device = room.mandatoryDevices().stream()
				.filter(d -> !d.finalLock())
				.findFirst()
				.orElseThrow();
		ApiContract contract = contracts.requireByChallengeId(device.challengeId());
		String wrongMethod = "GET".equals(contract.getExpectedMethod()) ? "POST" : "GET";
		simulate.simulate(agent, new SimulateRequestDto(agent, mission, contract.getDeviceId(),
				contract.getChallengeId(), wrongMethod, "/api/wrong", Map.of(), "", "MISSION"));
	}

	private static SimulateRequestDto correct(ApiContract contract) {
		return request(contract, contract.getExpectedMethod(), resolvePath(contract));
	}

	private static SimulateRequestDto request(ApiContract contract, String method, String path) {
		return new SimulateRequestDto("participant", contract.getMissionId(), contract.getDeviceId(),
				contract.getChallengeId(), method, path, Map.of(), "", "MISSION");
	}

	private static String resolvePath(ApiContract contract) {
		String path = contract.getPathTemplate();
		for (Map.Entry<String, String> entry : contract.getPathParameters().entrySet()) {
			path = path.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		if (!contract.getQueryParameters().isEmpty()) {
			StringBuilder query = new StringBuilder();
			for (Map.Entry<String, String> entry : contract.getQueryParameters().entrySet()) {
				query.append(query.isEmpty() ? "?" : "&").append(entry.getKey()).append('=').append(entry.getValue());
			}
			path = path + query;
		}
		return path;
	}
}
