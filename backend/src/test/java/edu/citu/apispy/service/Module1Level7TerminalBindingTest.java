package edu.citu.apispy.service;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Command Nexus: distinct physical devices ↔ distinct challenges; one unlock ≠ exit.
 */
class Module1Level7TerminalBindingTest {

	private static final String MISSION = "m1-recruit-l7";

	private ContractCatalogService contracts;
	private RequestSimulationService simulate;
	private MissionProgressService progress;
	private ScenarioSelectionService selection;

	@BeforeEach
	void setUp() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		contracts = bundle.contracts();
		simulate = bundle.service();
		progress = bundle.missionProgress();
		selection = new ScenarioSelectionService(bundle.escalation(), contracts);
	}

	@Test
	void bossRoomBindsSixDistinctObjectiveDevicesIncludingFinalLock() {
		MissionProgressDto initial = progress.getOrCreate("bind-agent", MISSION);
		assertEquals(5, initial.requiredDeviceCount());
		assertTrue(initial.finalLockRequired());
		assertEquals(6, initial.mandatoryDevices().size());
		assertEquals(6, initial.requiredDeviceIds().size());

		Set<String> deviceIds = new HashSet<>();
		Set<String> challengeIds = new HashSet<>();
		for (MissionProgressDto.MandatoryDeviceDto device : initial.mandatoryDevices()) {
			assertTrue(deviceIds.add(device.deviceId()), "duplicate device " + device.deviceId());
			assertTrue(challengeIds.add(device.challengeId()), "duplicate challenge " + device.challengeId());
		}
		assertTrue(initial.mandatoryDevices().stream().anyMatch(MissionProgressDto.MandatoryDeviceDto::finalLock));
		assertFalse(initial.exitReady());
	}

	@Test
	void solvingOneDeviceDoesNotUnlockExitOrSiblingDevices() {
		MissionProgressDto initial = progress.getOrCreate("one-device", MISSION);
		MissionProgressDto.MandatoryDeviceDto target = initial.mandatoryDevices().stream()
				.filter(d -> !d.finalLock())
				.findFirst()
				.orElseThrow();
		ApiContract contract = contracts.requireByChallengeId(target.challengeId());

		SimulateResponseDto ok = simulate.simulate(
				"one-device",
				request(
						MISSION,
						contract.getDeviceId(),
						contract.getChallengeId(),
						contract.getExpectedMethod(),
						resolvePath(contract)));
		assertTrue(ok.correct());
		assertTrue(ok.missionProgress().unlockedDeviceIds().contains(contract.getDeviceId()));
		assertEquals(1, ok.missionProgress().unlockedDeviceIds().size());
		assertFalse(ok.missionProgress().exitReady(), "single unlock must not open the boss exit");
		assertTrue(ok.adaptivePracticeDoors().isEmpty());

		for (MissionProgressDto.MandatoryDeviceDto sibling : ok.missionProgress().mandatoryDevices()) {
			if (sibling.deviceId().equals(contract.getDeviceId())) {
				assertTrue(sibling.unlocked());
			} else {
				assertFalse(sibling.unlocked(), sibling.deviceId() + " must stay locked");
			}
		}
	}

	@Test
	void fiveMandatoryWithoutDeleteKeepsExitLocked() {
		MissionProgressDto current = progress.getOrCreate("five-no-delete", MISSION);
		for (MissionProgressDto.MandatoryDeviceDto device : current.mandatoryDevices()) {
			if (device.finalLock()) {
				continue;
			}
			ApiContract contract = contracts.requireByChallengeId(device.challengeId());
			SimulateResponseDto ok = simulate.simulate(
					"five-no-delete",
					request(
							MISSION,
							contract.getDeviceId(),
							contract.getChallengeId(),
							contract.getExpectedMethod(),
							resolvePath(contract)));
			assertTrue(ok.correct(), device.deviceId());
			current = ok.missionProgress();
		}
		assertEquals(5, current.unlockedDeviceIds().size());
		assertFalse(current.unlockedDeviceIds().contains("final-lock-l7"));
		assertFalse(current.exitReady(), "DELETE final lock still required");
	}

	@Test
	void fivePlusDeleteDropsBossShieldThenDefeatUnlocksExit() {
		MissionProgressDto current = unlockAll("full-clear");
		assertTrue(current.boss().shieldDown());
		assertFalse(current.exitReady(), "boss must still be defeated");
		current = progress.reportBossDefeated("full-clear", MISSION, current.roomGeneration());
		assertTrue(current.exitReady());
		assertTrue(current.unlockedDeviceIds().contains("final-lock-l7"));
		assertEquals(6, current.unlockedDeviceIds().size());
	}

	@Test
	void verificationPlanExcludesOtherSelectedMandatoryChallenges() {
		MissionProgressDto initial = progress.getOrCreate("verify-pool", MISSION);
		List<String> selected = initial.mandatoryDevices().stream()
				.map(MissionProgressDto.MandatoryDeviceDto::challengeId)
				.toList();
		List<ApiContract> plan = selection.verificationPlan(
				MISSION,
				selected,
				java.util.UUID.fromString(initial.roomGeneration()));
		assertFalse(plan.isEmpty());
		String primary = selected.stream()
				.filter(id -> !id.startsWith("chal-m1-final-lock-"))
				.findFirst()
				.orElseThrow();
		assertEquals(primary, plan.getFirst().getChallengeId());
		if (plan.size() > 1) {
			String secondary = plan.get(1).getChallengeId();
			assertNotEquals(primary, secondary);
			assertFalse(
					selected.contains(secondary),
					"TVP follow-up must not be another selected mandatory challenge: " + secondary);
		}
	}

	@Test
	void refreshPreservesPerDeviceAssignments() {
		MissionProgressDto first = progress.getOrCreate("refresh-bind", MISSION);
		MissionProgressDto again = progress.getOrCreate("refresh-bind", MISSION);
		assertEquals(first.roomGeneration(), again.roomGeneration());
		assertEquals(
				first.mandatoryDevices().stream().map(MissionProgressDto.MandatoryDeviceDto::challengeId).toList(),
				again.mandatoryDevices().stream().map(MissionProgressDto.MandatoryDeviceDto::challengeId).toList());
	}

	@Test
	void physicalDevicesResolveDistinctObjectiveFamilies() {
		MissionProgressDto initial = progress.getOrCreate("obj-family", MISSION);
		List<MissionProgressDto.MandatoryDeviceDto> devices = initial.mandatoryDevices();
		assertEquals(6, devices.size());

		assertTrue(matchesFamily(devices.get(0), "GET", "/api/nexus/status", false));
		assertTrue(matchesFamily(devices.get(1), "GET", "/api/nexus/circuits/", true));
		assertTrue(matchesFamily(devices.get(2), "GET", "/api/nexus/alarms", true));
		assertTrue(matchesFamily(devices.get(3), "POST", "/api/nexus/breach", false));
		assertTrue(matchesFamily(devices.get(4), "PUT", "/api/nexus/lockdown", false));
		assertTrue(devices.get(5).finalLock());
		ApiContract finalLock = contracts.requireByChallengeId(devices.get(5).challengeId());
		assertEquals("DELETE", finalLock.getExpectedMethod());

		Set<String> families = new HashSet<>();
		for (int i = 0; i < 5; i++) {
			ApiContract c = contracts.requireByChallengeId(devices.get(i).challengeId());
			String family = c.getExpectedMethod() + ":" + pathFamily(c.getPathTemplate());
			assertTrue(families.add(family), "duplicate objective family " + family);
		}
	}

	@Test
	void openingTerminalBNeverShowsTerminalAChallenge() {
		MissionProgressDto initial = progress.getOrCreate("term-ab", MISSION);
		MissionProgressDto.MandatoryDeviceDto terminalA = initial.mandatoryDevices().get(0);
		MissionProgressDto.MandatoryDeviceDto terminalB = initial.mandatoryDevices().get(1);
		assertNotEquals(terminalA.challengeId(), terminalB.challengeId());
		assertNotEquals(terminalA.deviceId(), terminalB.deviceId());
		ApiContract a = contracts.requireByChallengeId(terminalA.challengeId());
		ApiContract b = contracts.requireByChallengeId(terminalB.challengeId());
		assertNotEquals(a.getPathTemplate(), b.getPathTemplate());
		assertNotEquals(a.getDeviceId(), b.getDeviceId());
	}

	@Test
	void optionalVerificationDoesNotMutateSiblingMandatoryDevices() {
		MissionProgressDto initial = progress.getOrCreate("opt-sib", MISSION);
		MissionProgressDto.MandatoryDeviceDto target = initial.mandatoryDevices().stream()
				.filter(d -> !d.finalLock())
				.findFirst()
				.orElseThrow();
		ApiContract contract = contracts.requireByChallengeId(target.challengeId());
		SimulateResponseDto unlocked = simulate.simulate(
				"opt-sib",
				request(
						MISSION,
						contract.getDeviceId(),
						contract.getChallengeId(),
						contract.getExpectedMethod(),
						resolvePath(contract)));
		assertTrue(unlocked.correct());
		assertFalse(unlocked.missionProgress().exitReady());

		var next = unlocked.terminalVerification().verifyAnotherScenario();
		if (next == null) {
			return;
		}
		assertNotEquals(contract.getChallengeId(), next.challengeId());
		Set<String> selected = new HashSet<>(
				unlocked.missionProgress().mandatoryDevices().stream()
						.map(MissionProgressDto.MandatoryDeviceDto::challengeId)
						.toList());
		assertFalse(
				selected.contains(next.challengeId()),
				"optional follow-up must not be another selected mandatory: " + next.challengeId());

		ApiContract optional = contracts.requireByChallengeId(next.challengeId());
		List<String> unlockedBefore = List.copyOf(unlocked.missionProgress().unlockedDeviceIds());
		SimulateResponseDto opt = simulate.simulate(
				"opt-sib",
				request(
						MISSION,
						optional.getDeviceId(),
						optional.getChallengeId(),
						optional.getExpectedMethod(),
						resolvePath(optional)));
		assertTrue(opt.correct());
		assertTrue(opt.optionalVerification());
		assertEquals(unlockedBefore, opt.missionProgress().unlockedDeviceIds());
		assertFalse(opt.missionProgress().exitReady());
		for (MissionProgressDto.MandatoryDeviceDto sibling : opt.missionProgress().mandatoryDevices()) {
			if (sibling.deviceId().equals(contract.getDeviceId())) {
				assertTrue(sibling.unlocked());
			} else {
				assertFalse(sibling.unlocked(), sibling.deviceId() + " must stay locked after optional");
			}
		}
	}

	@Test
	void repeatSubmissionCannotUnlockUnrelatedDevices() {
		MissionProgressDto initial = progress.getOrCreate("repeat-sub", MISSION);
		MissionProgressDto.MandatoryDeviceDto target = initial.mandatoryDevices().stream()
				.filter(d -> !d.finalLock())
				.findFirst()
				.orElseThrow();
		ApiContract contract = contracts.requireByChallengeId(target.challengeId());
		SimulateRequestDto req = request(
				MISSION,
				contract.getDeviceId(),
				contract.getChallengeId(),
				contract.getExpectedMethod(),
				resolvePath(contract));
		SimulateResponseDto first = simulate.simulate("repeat-sub", req);
		assertTrue(first.correct());
		SimulateResponseDto again = simulate.simulate("repeat-sub", req);
		assertTrue(again.correct());
		assertEquals(1, again.missionProgress().unlockedDeviceIds().size());
		assertTrue(again.missionProgress().unlockedDeviceIds().contains(contract.getDeviceId()));
		assertFalse(again.missionProgress().exitReady());
		assertEquals(
				first.missionProgress().unlockedDeviceIds(),
				again.missionProgress().unlockedDeviceIds());
	}

	@Test
	void nonBossRecruitLevelsKeepSingleDeviceOnFirstAttempt() {
		for (String missionId : List.of(
				"m1-recruit-l1",
				"m1-recruit-l2",
				"m1-recruit-l3",
				"m1-recruit-l4",
				"m1-recruit-l5",
				"m1-recruit-l6")) {
			MissionProgressDto progressDto = progress.getOrCreate("non-boss-" + missionId, missionId);
			assertEquals(1, progressDto.requiredDeviceCount(), missionId);
			assertFalse(progressDto.finalLockRequired(), missionId);
			assertEquals(1, progressDto.mandatoryDevices().size(), missionId);
			assertFalse(progressDto.exitReady(), missionId);
		}
	}

	private boolean matchesFamily(
			MissionProgressDto.MandatoryDeviceDto device,
			String method,
			String pathPrefix,
			boolean allowPathOrQueryExtras) {
		ApiContract contract = contracts.requireByChallengeId(device.challengeId());
		assertEquals(method, contract.getExpectedMethod(), device.challengeId());
		assertTrue(
				contract.getPathTemplate().startsWith(pathPrefix)
						|| contract.getPathTemplate().contains(pathPrefix.replace("/api/nexus/", "")),
				device.challengeId() + " path " + contract.getPathTemplate());
		if (!allowPathOrQueryExtras && method.equals("GET") && pathPrefix.equals("/api/nexus/status")) {
			assertTrue(contract.getPathParameters().isEmpty());
			assertTrue(contract.getQueryParameters().isEmpty());
		}
		if (pathPrefix.contains("circuits")) {
			assertFalse(contract.getPathParameters().isEmpty(), device.challengeId());
		}
		if (pathPrefix.contains("alarms")) {
			assertFalse(contract.getQueryParameters().isEmpty(), device.challengeId());
		}
		return true;
	}

	private static String pathFamily(String pathTemplate) {
		if (pathTemplate.contains("/circuits/")) {
			return "circuits";
		}
		if (pathTemplate.contains("/alarms")) {
			return "alarms";
		}
		if (pathTemplate.contains("/breach")) {
			return "breach";
		}
		if (pathTemplate.contains("/lockdown")) {
			return "lockdown";
		}
		if (pathTemplate.contains("/status")) {
			return "status";
		}
		return pathTemplate;
	}

	private MissionProgressDto unlockAll(String participant) {
		MissionProgressDto current = progress.getOrCreate(participant, MISSION);
		for (MissionProgressDto.MandatoryDeviceDto device : List.copyOf(current.mandatoryDevices())) {
			if (device.unlocked()) {
				continue;
			}
			ApiContract contract = contracts.requireByChallengeId(device.challengeId());
			SimulateResponseDto ok = simulate.simulate(
					participant,
					request(
							MISSION,
							contract.getDeviceId(),
							contract.getChallengeId(),
							contract.getExpectedMethod(),
							resolvePath(contract)));
			assertTrue(ok.correct(), device.deviceId());
			current = ok.missionProgress();
		}
		return current;
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
