package edu.citu.apispy.service;

import edu.citu.apispy.domain.mission.RoomResetReason;
import edu.citu.apispy.domain.research.ResearchEvent;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.MissionProgressDto;
import edu.citu.apispy.dto.SimulateRequestDto;
import edu.citu.apispy.service.research.InMemoryResearchEventLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gameplay flows write the hidden research log (SRS 2.3: completion, reset reason, rank, timing). */
class ResearchEventLoggingTest {

	private static final String MISSION = "m1-recruit-l1";

	private MissionProgressService progress;
	private RequestSimulationService simulate;
	private ContractCatalogService contracts;
	private InMemoryResearchEventLog log;

	@BeforeEach
	void setUp() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		progress = bundle.missionProgress();
		simulate = bundle.service();
		contracts = bundle.contracts();
		log = new InMemoryResearchEventLog();
		progress.setResearchEventLog(log);
	}

	private List<String> types(String who) {
		return log.all().stream().filter(e -> e.participantCode().equals(who)).map(e -> {
			String t = e.type();
			return e.reason() == null ? t : t + ":" + e.reason();
		}).toList();
	}

	@Test
	void roomLifecycleIsLoggedWithServerClassifiedResetReasons() {
		String who = "lifecycle";
		MissionProgressDto room = progress.getOrCreate(who, MISSION);
		progress.recordRoomEntered(who, MISSION, room.roomGeneration());

		// Wrong graded request, then a manual restart -> server classifies it as API-stalled.
		MissionProgressDto.MandatoryDeviceDto device = room.mandatoryDevices().getFirst();
		ApiContract contract = contracts.requireByChallengeId(device.challengeId());
		simulate.simulate(who, new SimulateRequestDto(who, MISSION, contract.getDeviceId(), contract.getChallengeId(),
				"DELETE", "/api/wrong", Map.of(), "", "MISSION"));
		MissionProgressDto second = progress.resetRoom(who, MISSION, RoomResetReason.PLAYER, room.roomGeneration());

		// Combat death in the new room.
		progress.reportCombatDeath(who, MISSION, second.roomGeneration());
		MissionProgressDto third = progress.resetRoom(who, MISSION, RoomResetReason.COMBAT);

		assertEquals(List.of(
				"ROOM_START", "ROOM_ENTERED", "ROOM_RESET:API_STALLED", "ROOM_START",
				"COMBAT_DEATH", "ROOM_RESET:COMBAT", "ROOM_START"), types(who));

		List<ResearchEvent> events = log.all();
		ResearchEvent first = events.getFirst();
		assertEquals(1, first.module());
		assertEquals("RECRUIT", first.rank());
		assertEquals(1, first.level());
		assertEquals("PRIMARY", first.runKind());
		assertEquals(UUID.fromString(room.roomGeneration()), first.roomGeneration());
		assertEquals(UUID.fromString(third.roomGeneration()), events.getLast().roomGeneration());
	}

	@Test
	void completionAndReplayAreLoggedWithTheirRunKind() {
		String who = "clearer";
		MissionProgressDto room = progress.getOrCreate(who, MISSION);
		for (MissionProgressDto.MandatoryDeviceDto device : room.mandatoryDevices()) {
			ApiContract c = contracts.requireByChallengeId(device.challengeId());
			String path = c.getPathTemplate();
			for (Map.Entry<String, String> p : c.getPathParameters().entrySet()) {
				path = path.replace("{" + p.getKey() + "}", p.getValue());
			}
			simulate.simulate(who, new SimulateRequestDto(who, MISSION, c.getDeviceId(), c.getChallengeId(),
					c.getExpectedMethod(), path, Map.of(), "", "MISSION"));
		}
		progress.completeAfterExit(who, MISSION, progress.getOrCreate(who, MISSION).roomGeneration());
		progress.startReplay(who, MISSION);

		List<ResearchEvent> mine = log.all().stream().filter(e -> e.participantCode().equals(who)).toList();
		assertEquals(List.of("ROOM_START", "ROOM_COMPLETE", "ROOM_START"), mine.stream().map(ResearchEvent::type).toList());
		assertEquals("PRIMARY", mine.get(1).runKind(), "completion logged before the run closes");
		assertEquals("REPLAY", mine.get(2).runKind());
	}

	@Test
	void terminalOpensAreOnlyLoggedForRequiredDevicesInTheActiveRoom() {
		String who = "opener";
		MissionProgressDto room = progress.getOrCreate(who, MISSION);
		String device = room.mandatoryDevices().getFirst().deviceId();

		progress.recordTerminalOpened(who, MISSION, room.roomGeneration(), device);
		progress.recordTerminalOpened(who, MISSION, room.roomGeneration(), "not-a-device-here");
		progress.recordTerminalOpened(who, MISSION, UUID.randomUUID().toString(), device);
		progress.recordRoomEntered(who, MISSION, UUID.randomUUID().toString());

		List<ResearchEvent> opens = log.all().stream()
				.filter(e -> e.participantCode().equals(who) && e.type().equals(ResearchEvent.TERMINAL_OPENED))
				.toList();
		assertEquals(1, opens.size());
		assertEquals(device, opens.getFirst().deviceId());
		assertTrue(types(who).stream().noneMatch(t -> t.equals(ResearchEvent.ROOM_ENTERED)), "stale room entry ignored");
	}
}
