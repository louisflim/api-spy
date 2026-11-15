package edu.citu.apispy.service;

import edu.citu.apispy.domain.mission.MissionProgressStatus;
import edu.citu.apispy.dto.MissionProgressDto;
import edu.citu.apispy.dto.SimulateRequestDto;
import edu.citu.apispy.repository.MissionProgressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies mission completion persists in Supabase PostgreSQL via JPA
 * and cannot be forged without mandatory device activation + matching room generation.
 */
@SpringBootTest
class MissionProgressPersistenceIntegrationTest {

	@Autowired
	private MissionProgressService missionProgressService;

	@Autowired
	private RequestSimulationService requestSimulationService;

	@Autowired
	private MissionProgressRepository missionProgressRepository;

	@Test
	void completionPersistsAcrossReloadAndRejectsForgery() {
		String playerId = "mp-" + UUID.randomUUID();
		String otherPlayer = "mp-other-" + UUID.randomUUID();

		MissionProgressDto created = missionProgressService.getOrCreate(playerId, "m1-recruit-l1");
		assertThrows(
				IllegalStateException.class,
				() -> missionProgressService.completeAfterExit(
						playerId, "m1-recruit-l1", created.roomGeneration()));
		assertThrows(
				IllegalArgumentException.class,
				() -> missionProgressService.completeAfterExit(playerId, "m1-recruit-l1", null));

		requestSimulationService.simulate(
				playerId,
				new SimulateRequestDto(
						"participant",
						"m1-recruit-l1",
						"door-alpha",
						"chal-m1-get-door-v1",
						"GET",
						"/api/doors/alpha",
						Map.of(),
						"",
						"MISSION"));

		MissionProgressDto exitReady = missionProgressService.getOrCreate(playerId, "m1-recruit-l1");
		assertTrue(exitReady.exitReady());
		assertFalse(exitReady.completed());

		MissionProgressDto completed = missionProgressService.completeAfterExit(
				playerId, "m1-recruit-l1", exitReady.roomGeneration());
		assertTrue(completed.completed());
		assertEquals(MissionProgressStatus.COMPLETED.name(), completed.status());

		MissionProgressDto reloaded = missionProgressService.getOrCreate(playerId, "m1-recruit-l1");
		assertTrue(reloaded.completed());
		assertTrue(
				missionProgressRepository
						.findByParticipantCodeAndMissionId(playerId, "m1-recruit-l1")
						.isPresent());

		MissionProgressDto isolated = missionProgressService.getOrCreate(otherPlayer, "m1-recruit-l1");
		assertFalse(isolated.completed());
		assertTrue(isolated.unlockedDeviceIds().isEmpty());

		boolean listed = missionProgressService.listForParticipant(playerId).stream()
				.anyMatch(row -> row.missionId().equals("m1-recruit-l1") && row.completed());
		assertTrue(listed);
	}
}
