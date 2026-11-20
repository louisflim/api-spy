package edu.citu.apispy.domain.research;

import java.time.Instant;
import java.util.UUID;

/**
 * One hidden gameplay event for research reporting (never shown to players).
 *
 * @param type        ROOM_START, ROOM_ENTERED, ROOM_RESET, COMBAT_DEATH, ROOM_COMPLETE, BOSS_DEFEATED, TERMINAL_OPENED
 * @param reason      ROOM_RESET only: API_STALLED, COMBAT or PLAYER (server-classified)
 * @param runKind     PRIMARY or REPLAY
 */
public record ResearchEvent(
		UUID id,
		String participantCode,
		String missionId,
		int module,
		String rank,
		int level,
		UUID roomGeneration,
		String type,
		String reason,
		String deviceId,
		String runKind,
		Integer apiResetCount,
		Instant occurredAt
) {
	public static final String ROOM_START = "ROOM_START";
	/** The agent actually loaded the field room for this generation. */
	public static final String ROOM_ENTERED = "ROOM_ENTERED";
	public static final String ROOM_RESET = "ROOM_RESET";
	public static final String COMBAT_DEATH = "COMBAT_DEATH";
	public static final String ROOM_COMPLETE = "ROOM_COMPLETE";
	public static final String BOSS_DEFEATED = "BOSS_DEFEATED";
	public static final String TERMINAL_OPENED = "TERMINAL_OPENED";
	/** Descriptive gameplay variable (proposal): never part of SCR, RCA or HLCR. */
	public static final String ENEMY_DEFEATED = "ENEMY_DEFEATED";
}
