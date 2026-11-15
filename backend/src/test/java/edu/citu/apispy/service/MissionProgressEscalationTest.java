package edu.citu.apispy.service;

import edu.citu.apispy.domain.mission.RoomResetReason;
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

	@BeforeEach
	void setUp() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		simulate = bundle.service();
		progress = bundle.missionProgress();
		escalation = bundle.escalation();
	}

	@Test
	void escalationFollowsOneThenThreeThenFivePlusFinalLock() {
		assertEquals(1, escalation.mandatoryDeviceCount(0));
		assertEquals(3, escalation.mandatoryDeviceCount(1));
		assertEquals(5, escalation.mandatoryDeviceCount(2));
		assertFalse(escalation.finalLockRequired(0));
		assertTrue(escalation.finalLockRequired(1));

		MissionProgressDto initial = progress.getOrCreate("p1", "m1-recruit-l1");
		assertEquals(1, initial.requiredDeviceIds().size());
		assertEquals("door-alpha", initial.requiredDeviceIds().getFirst());

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
	void playerResetRequiresMatchingGenerationAndDoesNotEscalate() {
		MissionProgressDto initial = progress.getOrCreate("player-reset", "m1-recruit-l1");
		assertEquals(0, initial.apiResetCount());
		assertEquals(1, initial.requiredDeviceIds().size());

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
		assertEquals("PRIMARY", after.activeRunKind());
		assertNotEquals(initial.roomGeneration(), after.roomGeneration());
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
		SimulateResponseDto unlock = simulate.simulate(
				"replay-reset",
				request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "GET", "/api/doors/alpha"));
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
	void adaptivePracticeDevicesNeverCountAsMandatory() {
		assertFalse(escalation.isMandatoryDevice("m1-recruit-l1", "practice-panel", 2));
		assertTrue(escalation.isPracticeDevice("practice-panel"));
	}

	@Test
	void allMandatoryDevicesRequiredBeforeExitReadyAndPhysicalComplete() {
		SimulateResponseDto unlock = simulate.simulate(
				"exit-agent",
				request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "GET", "/api/doors/alpha"));
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
		SimulateResponseDto unlock = simulate.simulate(
				"iso-a",
				request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "GET", "/api/doors/alpha"));
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
		SimulateResponseDto unlock = simulate.simulate(
				"persist-agent",
				request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "GET", "/api/doors/alpha"));
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
		SimulateResponseDto unlock = simulate.simulate(
				"leak-check",
				request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "GET", "/api/doors/alpha"));
		MissionProgressDto.MandatoryDeviceDto device = unlock.missionProgress().mandatoryDevices().getFirst();
		assertEquals("door-alpha", device.deviceId());
		assertTrue(device.documentation() != null && !device.documentation().isBlank());
	}

	private static final String UUID_OTHER = "00000000-0000-0000-0000-000000000099";

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
