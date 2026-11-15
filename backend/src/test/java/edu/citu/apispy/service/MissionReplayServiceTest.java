package edu.citu.apispy.service;

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

class MissionReplayServiceTest {

	private RequestSimulationService simulate;
	private MissionProgressService progress;
	private FirstAttemptService firstAttempts;

	@BeforeEach
	void setUp() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		simulate = bundle.service();
		progress = bundle.missionProgress();
		firstAttempts = bundle.firstAttempts();
	}

	@Test
	void replayRequiresCareerClearThenResetsDevicesAndLocksExit() {
		assertThrows(
				IllegalStateException.class,
				() -> progress.startReplay("r1", "m1-recruit-l1"));

		SimulateResponseDto unlock = simulate.simulate(
				"r1",
				request("GET", "/api/doors/alpha"));
		String firstGen = unlock.missionProgress().roomGeneration();
		MissionProgressDto cleared = progress.completeAfterExit("r1", "m1-recruit-l1", firstGen);
		assertTrue(cleared.completed());
		assertTrue(cleared.replayable());
		assertFalse(cleared.replayActive());
		assertEquals("NONE", cleared.activeRunKind());

		MissionProgressDto replay = progress.startReplay("r1", "m1-recruit-l1");
		assertTrue(replay.completed(), "career completion must remain");
		assertTrue(replay.replayActive());
		assertFalse(replay.replayable());
		assertEquals("REPLAY", replay.activeRunKind());
		assertEquals(1, replay.replayCount());
		assertEquals(0, replay.apiResetCount());
		assertTrue(replay.unlockedDeviceIds().isEmpty());
		assertFalse(replay.exitReady());
		assertNotEquals(firstGen, replay.roomGeneration());
	}

	@Test
	void replayCompletePreservesCareerClearAndFirstAttempt() {
		simulate.simulate("r2", request("GET", "/api/doors/alpha"));
		MissionProgressDto first = progress.getOrCreate("r2", "m1-recruit-l1");
		progress.completeAfterExit("r2", "m1-recruit-l1", first.roomGeneration());
		assertTrue(firstAttempts.find("r2", "m1-recruit-l1", "door-alpha").isPresent());
		String originalCompletedAt = progress.getOrCreate("r2", "m1-recruit-l1").completedAt();

		MissionProgressDto replay = progress.startReplay("r2", "m1-recruit-l1");
		SimulateResponseDto again = simulate.simulate("r2", request("GET", "/api/doors/alpha"));
		assertTrue(again.correct());
		assertFalse(again.firstAttemptRecorded(), "replay must not rewrite RCA first attempts");
		assertTrue(again.missionProgress().exitReady());

		MissionProgressDto afterReplay = progress.completeAfterExit(
				"r2", "m1-recruit-l1", again.missionProgress().roomGeneration());
		assertTrue(afterReplay.completed());
		assertFalse(afterReplay.replayActive());
		assertTrue(afterReplay.replayable());
		assertEquals(originalCompletedAt, afterReplay.completedAt());
		assertEquals(1, firstAttempts.findAllForParticipant("r2").size());
	}

	@Test
	void forgedReplayCompleteWithoutDevicesIsRejected() {
		simulate.simulate("r3", request("GET", "/api/doors/alpha"));
		MissionProgressDto first = progress.getOrCreate("r3", "m1-recruit-l1");
		progress.completeAfterExit("r3", "m1-recruit-l1", first.roomGeneration());
		MissionProgressDto replay = progress.startReplay("r3", "m1-recruit-l1");
		assertThrows(
				IllegalStateException.class,
				() -> progress.completeAfterExit("r3", "m1-recruit-l1", replay.roomGeneration()));
	}

	@Test
	void replayProgressIsIsolatedBetweenPlayers() {
		simulate.simulate("iso-r-a", request("GET", "/api/doors/alpha"));
		MissionProgressDto a = progress.getOrCreate("iso-r-a", "m1-recruit-l1");
		progress.completeAfterExit("iso-r-a", "m1-recruit-l1", a.roomGeneration());
		progress.startReplay("iso-r-a", "m1-recruit-l1");

		MissionProgressDto b = progress.getOrCreate("iso-r-b", "m1-recruit-l1");
		assertFalse(b.completed());
		assertFalse(b.replayActive());
	}

	private static SimulateRequestDto request(String method, String path) {
		return new SimulateRequestDto(
				"participant",
				"m1-recruit-l1",
				"door-alpha",
				"chal-m1-get-door-v1",
				method,
				path,
				Map.of(),
				"",
				"MISSION");
	}
}
