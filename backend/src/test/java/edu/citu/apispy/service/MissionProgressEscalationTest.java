package edu.citu.apispy.service;

import edu.citu.apispy.domain.mission.RoomResetReason;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionProgressEscalationTest {

	private RequestSimulationService simulate;
	private MissionProgressService progress;
	private DeviceEscalationService escalation;
	private ContractCatalogService contracts;

	@BeforeEach
	void setUp() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		simulate = bundle.service();
		progress = bundle.missionProgress();
		escalation = bundle.escalation();
		contracts = bundle.contracts();
	}

	@Test
	void escalationFollowsOneThenThreeThenFivePlusFinalLock() {
		assertEquals(1, escalation.mandatoryDeviceCount(0));
		assertEquals(3, escalation.mandatoryDeviceCount(1));
		assertEquals(5, escalation.mandatoryDeviceCount(2));
		assertFalse(escalation.finalLockRequired(0));
		assertTrue(escalation.finalLockRequired(1));

		MissionProgressDto initial = progress.getOrCreate("p1", "m1-recruit-l1");
		assertEquals(1, initial.requiredDeviceIds().size()); // pool-selected primary (seeded by roomGeneration)

		assertThrows(
				IllegalStateException.class,
				() -> progress.resetRoom("p1", "m1-recruit-l1", RoomResetReason.API_STALLED));

		simulate.simulate("p1", request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "POST", "/api/doors/wrong"));

		MissionProgressDto afterApi = progress.resetRoom("p1", "m1-recruit-l1", RoomResetReason.API_STALLED);
		assertEquals(1, afterApi.apiResetCount());
		assertEquals(3, afterApi.requiredDeviceCount());
		assertEquals(4, afterApi.requiredDeviceIds().size()); // 3 + final lock
		assertTrue(afterApi.requiredDeviceIds().contains("final-lock-l1"));
		assertTrue(afterApi.unlockedDeviceIds().isEmpty());
		assertEquals(0, afterApi.gradedAttemptCountSinceReset());
		assertNotEquals(initial.roomGeneration(), afterApi.roomGeneration());

		simulate.simulate("p1", request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "POST", "/api/doors/wrong"));
		MissionProgressDto afterSecond = progress.resetRoom("p1", "m1-recruit-l1", RoomResetReason.API_STALLED);
		assertEquals(2, afterSecond.apiResetCount());
		assertEquals(5, afterSecond.requiredDeviceCount());
		assertEquals(6, afterSecond.requiredDeviceIds().size()); // 5 + final lock
	}

	@Test
	void playerResetRequiresMatchingGenerationAndDoesNotEscalateWithoutApiActivity() {
		MissionProgressDto initial = progress.getOrCreate("player-reset", "m1-recruit-l1");
		assertEquals(0, initial.apiResetCount());
		assertEquals(1, initial.requiredDeviceIds().size());
		assertFalse(initial.manualRestartWouldEscalate());

		assertThrows(
				IllegalArgumentException.class,
				() -> progress.resetRoom("player-reset", "m1-recruit-l1", RoomResetReason.PLAYER, null));
		assertThrows(
				IllegalStateException.class,
				() -> progress.resetRoom(
						"player-reset",
						"m1-recruit-l1",
						RoomResetReason.PLAYER,
						"00000000-0000-0000-0000-000000000099"));

		MissionProgressDto after = progress.resetRoom(
				"player-reset",
				"m1-recruit-l1",
				RoomResetReason.PLAYER,
				initial.roomGeneration());
		assertEquals(0, after.apiResetCount());
		assertEquals(1, after.requiredDeviceCount());
		assertEquals(1, after.requiredDeviceIds().size());
		assertTrue(after.unlockedDeviceIds().isEmpty());
		assertEquals(0, after.gradedAttemptCountSinceReset());
		assertEquals(0, after.consecutiveUnresolvedApiResets());
		assertEquals("PRIMARY", after.activeRunKind());
		assertNotEquals(initial.roomGeneration(), after.roomGeneration());
	}

	@Test
	void wrongSubmissionsAloneDoNotEscalateUntilApiDrivenRoomReset() {
		progress.getOrCreate("wrong-only", "m1-recruit-l1");
		for (int i = 0; i < 5; i++) {
			simulate.simulate(
					"wrong-only",
					request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "POST", "/api/doors/wrong"));
		}
		MissionProgressDto afterWrongs = progress.getOrCreate("wrong-only", "m1-recruit-l1");
		assertEquals(0, afterWrongs.apiResetCount());
		assertEquals(1, afterWrongs.requiredDeviceCount());
		assertTrue(afterWrongs.gradedAttemptCountSinceReset() >= 5);
		assertTrue(afterWrongs.manualRestartWouldEscalate());
		assertEquals(3, afterWrongs.nextRequiredDeviceCount());
	}

	@Test
	void manualRestartAfterUnresolvedApiActivityEscalatesOneToThree() {
		MissionProgressDto initial = progress.getOrCreate("abandon", "m1-recruit-l1");
		simulate.simulate(
				"abandon",
				request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "POST", "/api/doors/wrong"));
		MissionProgressDto after = progress.resetRoom(
				"abandon",
				"m1-recruit-l1",
				RoomResetReason.PLAYER,
				initial.roomGeneration());
		assertEquals(1, after.apiResetCount());
		assertEquals(3, after.requiredDeviceCount());
		assertEquals(4, after.requiredDeviceIds().size());
		assertTrue(after.requiredDeviceIds().contains("final-lock-l1"));
		assertEquals(1, after.consecutiveUnresolvedApiResets());
	}

	@Test
	void playerResetKeepsEscalatedTierAndReplayKind() {
		progress.getOrCreate("tier-reset", "m1-recruit-l1");
		simulate.simulate(
				"tier-reset",
				request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "POST", "/api/doors/wrong"));
		MissionProgressDto escalated = progress.resetRoom("tier-reset", "m1-recruit-l1", RoomResetReason.API_STALLED);
		assertEquals(1, escalated.apiResetCount());
		assertEquals(3, escalated.requiredDeviceCount());
		assertEquals(4, escalated.requiredDeviceIds().size());

		MissionProgressDto manual = progress.resetRoom(
				"tier-reset",
				"m1-recruit-l1",
				RoomResetReason.PLAYER,
				escalated.roomGeneration());
		assertEquals(1, manual.apiResetCount());
		assertEquals(3, manual.requiredDeviceCount());
		assertEquals(4, manual.requiredDeviceIds().size());
		assertTrue(manual.requiredDeviceIds().contains("final-lock-l1"));
		assertTrue(manual.unlockedDeviceIds().isEmpty());
		assertEquals("PRIMARY", manual.activeRunKind());

		// Career-complete then replay: PLAYER reset must stay REPLAY.
		SimulateResponseDto unlock = simulate.simulate("replay-reset", correctSelectedPrimary("replay-reset"));
		assertTrue(unlock.correct());
		MissionProgressDto cleared = progress.completeAfterExit(
				"replay-reset",
				"m1-recruit-l1",
				unlock.missionProgress().roomGeneration());
		assertTrue(cleared.completed());
		assertEquals("NONE", cleared.activeRunKind());

		MissionProgressDto replay = progress.startReplay("replay-reset", "m1-recruit-l1");
		assertEquals("REPLAY", replay.activeRunKind());
		assertTrue(replay.replayActive());
		assertEquals(0, replay.apiResetCount());

		MissionProgressDto replayReset = progress.resetRoom(
				"replay-reset",
				"m1-recruit-l1",
				RoomResetReason.PLAYER,
				replay.roomGeneration());
		assertEquals("REPLAY", replayReset.activeRunKind());
		assertTrue(replayReset.replayActive());
		assertTrue(replayReset.completed());
		assertEquals(0, replayReset.apiResetCount());
		assertNotEquals(replay.roomGeneration(), replayReset.roomGeneration());
	}

	@Test
	void combatOnlyRestartDoesNotIncreaseDeviceCount() {
		progress.getOrCreate("combat", "m1-recruit-l1");
		simulate.simulate("combat", request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "GET", "/api/doors/alpha"));
		assertThrows(
				IllegalStateException.class,
				() -> progress.resetRoom("combat", "m1-recruit-l1", RoomResetReason.COMBAT));

		progress.authorizeCombatRestart("combat", "m1-recruit-l1");
		String generationBefore = progress.getOrCreate("combat", "m1-recruit-l1").roomGeneration();
		MissionProgressDto afterCombat = progress.resetRoom("combat", "m1-recruit-l1", RoomResetReason.COMBAT);
		assertEquals(0, afterCombat.apiResetCount());
		assertEquals(1, afterCombat.requiredDeviceIds().size());
		assertTrue(afterCombat.unlockedDeviceIds().isEmpty());
		assertFalse(afterCombat.combatRestartAuthorized());
		assertNotEquals(generationBefore, afterCombat.roomGeneration());
	}

	@Test
	void combatDeathRequiresMatchingRoomGenerationThenAllowsCombatResetWithoutApiEscalation() {
		MissionProgressDto created = progress.getOrCreate("death-agent", "m1-recruit-l1");
		assertThrows(
				IllegalArgumentException.class,
				() -> progress.reportCombatDeath("death-agent", "m1-recruit-l1", null));
		assertThrows(
				IllegalStateException.class,
				() -> progress.reportCombatDeath("death-agent", "m1-recruit-l1", UUID_OTHER));

		MissionProgressDto authorized = progress.reportCombatDeath(
				"death-agent", "m1-recruit-l1", created.roomGeneration());
		assertTrue(authorized.combatRestartAuthorized());
		assertEquals(0, authorized.apiResetCount());

		MissionProgressDto after = progress.resetRoom("death-agent", "m1-recruit-l1", RoomResetReason.COMBAT);
		assertEquals(0, after.apiResetCount());
		assertEquals(1, after.requiredDeviceIds().size());
		assertFalse(after.combatRestartAuthorized());
		assertNotEquals(created.roomGeneration(), after.roomGeneration());
	}

	@Test
	void furtherApiResetsRemainCappedAtFiveDevices() {
		progress.getOrCreate("cap", "m1-recruit-l1");
		simulate.simulate("cap", request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "POST", "/bad"));
		MissionProgressDto first = progress.resetRoom("cap", "m1-recruit-l1", RoomResetReason.API_STALLED);
		simulate.simulate("cap", request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "POST", "/bad"));
		MissionProgressDto second = progress.resetRoom("cap", "m1-recruit-l1", RoomResetReason.API_STALLED);
		assertEquals(2, second.apiResetCount());
		assertEquals(5, second.requiredDeviceCount());
		assertEquals(6, second.requiredDeviceIds().size());
		simulate.simulate("cap", request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "POST", "/bad"));
		MissionProgressDto third = progress.resetRoom(
				"cap", "m1-recruit-l1", RoomResetReason.PLAYER, second.roomGeneration());
		assertEquals(2, third.apiResetCount());
		assertEquals(5, third.requiredDeviceCount());
		assertEquals(6, third.requiredDeviceIds().size());
		assertEquals(3, third.consecutiveUnresolvedApiResets());
		assertEquals(first.requiredDeviceCount(), 3);
	}

	@Test
	void fifteenUnresolvedApiRoomAttemptsRequireLessonAndBlockBypass() {
		MissionProgressDto state = progress.getOrCreate("fifteen", "m1-recruit-l1");
		for (int i = 0; i < MissionProgressService.CONSECUTIVE_UNRESOLVED_API_RESET_THRESHOLD; i++) {
			simulate.simulate(
					"fifteen",
					request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "POST", "/api/doors/wrong"));
			state = progress.resetRoom(
					"fifteen",
					"m1-recruit-l1",
					RoomResetReason.PLAYER,
					state.roomGeneration());
		}
		assertTrue(state.lessonReviewRequired());
		assertEquals("rest-fundamentals", state.requiredReviewLessonId());
		assertEquals(
				MissionProgressService.CONSECUTIVE_UNRESOLVED_API_RESET_THRESHOLD,
				state.consecutiveUnresolvedApiResets());
		// Tier preserved at cap; gate does not alter device count further.
		assertEquals(2, state.apiResetCount());
		assertEquals(5, state.requiredDeviceCount());

		final String gatedGeneration = state.roomGeneration();
		assertThrows(
				IllegalStateException.class,
				() -> progress.resetRoom(
						"fifteen", "m1-recruit-l1", RoomResetReason.PLAYER, gatedGeneration));
		assertThrows(
				IllegalStateException.class,
				() -> simulate.simulate(
						"fifteen",
						request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "GET", "/api/doors/alpha")));

		MissionProgressDto cleared = progress.acknowledgeLessonReview(
				"fifteen", "m1-recruit-l1", "rest-fundamentals");
		assertFalse(cleared.lessonReviewRequired());
		assertEquals(0, cleared.consecutiveUnresolvedApiResets());
		assertEquals(2, cleared.apiResetCount());
		assertEquals(5, cleared.requiredDeviceCount());
	}

	@Test
	void fifteenWrongSubmissionsAloneDoNotTriggerLessonGate() {
		progress.getOrCreate("spam", "m1-recruit-l1");
		for (int i = 0; i < 15; i++) {
			simulate.simulate(
					"spam",
					request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "POST", "/api/doors/wrong"));
		}
		MissionProgressDto state = progress.getOrCreate("spam", "m1-recruit-l1");
		assertFalse(state.lessonReviewRequired());
		assertEquals(0, state.consecutiveUnresolvedApiResets());
		assertEquals(0, state.apiResetCount());
	}

	@Test
	void combatDeathDoesNotIncrementConsecutiveApiResets() {
		MissionProgressDto created = progress.getOrCreate("combat-streak", "m1-recruit-l1");
		simulate.simulate(
				"combat-streak",
				request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "POST", "/bad"));
		MissionProgressDto afterApi = progress.resetRoom(
				"combat-streak",
				"m1-recruit-l1",
				RoomResetReason.API_STALLED);
		assertEquals(1, afterApi.consecutiveUnresolvedApiResets());
		progress.reportCombatDeath("combat-streak", "m1-recruit-l1", afterApi.roomGeneration());
		MissionProgressDto afterCombat = progress.resetRoom(
				"combat-streak", "m1-recruit-l1", RoomResetReason.COMBAT);
		assertEquals(1, afterCombat.apiResetCount());
		assertEquals(3, afterCombat.requiredDeviceCount());
		assertEquals(1, afterCombat.consecutiveUnresolvedApiResets());
		assertEquals(1, created.requiredDeviceCount());
	}

	@Test
	void adaptivePracticeDevicesNeverCountAsMandatory() {
		assertFalse(escalation.isMandatoryDevice("m1-recruit-l1", "practice-panel", 2));
		assertTrue(escalation.isPracticeDevice("practice-panel"));
	}

	@Test
	void allMandatoryDevicesRequiredBeforeExitReadyAndPhysicalComplete() {
		SimulateResponseDto unlock = simulate.simulate("exit-agent", correctSelectedPrimary("exit-agent"));
		assertTrue(unlock.correct());
		assertTrue(unlock.missionProgress().exitReady());
		assertFalse(unlock.missionProgress().completed());

		String generation = unlock.missionProgress().roomGeneration();
		assertThrows(
				IllegalArgumentException.class,
				() -> progress.completeAfterExit("exit-agent", "m1-recruit-l1", null));
		assertThrows(
				IllegalStateException.class,
				() -> progress.completeAfterExit("exit-agent", "m1-recruit-l1", UUID_OTHER));

		MissionProgressDto completed = progress.completeAfterExit("exit-agent", "m1-recruit-l1", generation);
		assertTrue(completed.completed());
		assertEquals("COMPLETED", completed.status());
		assertEquals("NONE", completed.activeRunKind());
		assertTrue(completed.replayable());

		MissionProgressDto idempotent = progress.completeAfterExit("exit-agent", "m1-recruit-l1", generation);
		assertTrue(idempotent.completed());
	}

	@Test
	void forgedCompleteWithoutDevicesIsRejected() {
		MissionProgressDto created = progress.getOrCreate("forger", "m1-recruit-l1");
		assertThrows(
				IllegalStateException.class,
				() -> progress.completeAfterExit("forger", "m1-recruit-l1", created.roomGeneration()));
	}

	@Test
	void progressIsIsolatedBetweenPlayers() {
		SimulateResponseDto unlock = simulate.simulate("iso-a", correctSelectedPrimary("iso-a"));
		progress.completeAfterExit("iso-a", "m1-recruit-l1", unlock.missionProgress().roomGeneration());
		MissionProgressDto other = progress.getOrCreate("iso-b", "m1-recruit-l1");
		assertFalse(other.completed());
		assertTrue(other.unlockedDeviceIds().isEmpty());
	}

	@Test
	void httpSuccessAloneDoesNotMarkMissionCompleted() {
		SimulateResponseDto response = simulate.simulate(
				"http-only",
				request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "GET", "/api/doors/alpha"));
		assertEquals(200, response.simulatedResponse().status());
		assertFalse(response.missionProgress().completed());
	}

	@Test
	void completedProgressSurvivesServiceReloadAndAppearsOnOpsBoardList() {
		SimulateResponseDto unlock = simulate.simulate("persist-agent", correctSelectedPrimary("persist-agent"));
		progress.completeAfterExit("persist-agent", "m1-recruit-l1", unlock.missionProgress().roomGeneration());

		MissionProgressDto reloaded = progress.getOrCreate("persist-agent", "m1-recruit-l1");
		assertTrue(reloaded.completed());
		assertEquals("COMPLETED", reloaded.status());
		assertTrue(reloaded.exitReady());

		boolean listed = progress.listForParticipant("persist-agent").stream()
				.anyMatch(row -> row.missionId().equals("m1-recruit-l1") && row.completed());
		assertTrue(listed, "Operations Board list must include persisted completed mission");
	}

	@Test
	void gradedProgressResponsesOmitAnswerKeys() {
		MissionProgressDto initial = progress.getOrCreate("leak-check", "m1-recruit-l1");
		MissionProgressDto.MandatoryDeviceDto selected = initial.mandatoryDevices().getFirst();
		ApiContract contract = contracts.requireByChallengeId(selected.challengeId());
		SimulateResponseDto unlock = simulate.simulate(
				"leak-check",
				request(
						"m1-recruit-l1",
						contract.getDeviceId(),
						contract.getChallengeId(),
						contract.getExpectedMethod(),
						contract.getPathTemplate()));
		MissionProgressDto.MandatoryDeviceDto device = unlock.missionProgress().mandatoryDevices().getFirst();
		assertEquals(selected.deviceId(), device.deviceId());
		assertTrue(device.documentation() != null && !device.documentation().isBlank());
		assertFalse(device.documentation().contains(contract.getExpectedMethod() + " " + contract.getPathTemplate()));
	}

	private static final String UUID_OTHER = "00000000-0000-0000-0000-000000000099";

	private SimulateRequestDto correctSelectedPrimary(String participantCode) {
		MissionProgressDto initial = progress.getOrCreate(participantCode, "m1-recruit-l1");
		MissionProgressDto.MandatoryDeviceDto selected = initial.mandatoryDevices().getFirst();
		ApiContract contract = contracts.requireByChallengeId(selected.challengeId());
		String path = contract.getPathTemplate();
		for (var entry : contract.getPathParameters().entrySet()) {
			path = path.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		if (!contract.getQueryParameters().isEmpty()) {
			StringBuilder query = new StringBuilder("?");
			boolean first = true;
			for (var entry : contract.getQueryParameters().entrySet()) {
				if (!first) {
					query.append('&');
				}
				query.append(entry.getKey()).append('=').append(entry.getValue());
				first = false;
			}
			path = path + query;
		}
		return request(
				"m1-recruit-l1",
				contract.getDeviceId(),
				contract.getChallengeId(),
				contract.getExpectedMethod(),
				path);
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
