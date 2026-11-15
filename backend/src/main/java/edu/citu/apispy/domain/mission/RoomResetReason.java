package edu.citu.apispy.domain.mission;

/**
 * Distinguishes API-driven room resets from combat-only and voluntary restarts (SRS).
 * Combat-only restarts must never change {@code apiResetCount} (1→3→5 tier).
 * <p>
 * Clients cannot freely choose gated reasons; the server reclassifies {@link #PLAYER}
 * when the active room has unresolved graded API activity:
 * <ul>
 *   <li>{@link #API_STALLED} requires server-tracked graded activity / unlocks since the
 *       last reset; increments {@code apiResetCount} (capped) and consecutive unresolved
 *       API-room attempts.</li>
 *   <li>{@link #COMBAT} requires a prior server-side combat-death authorization flag;
 *       does not escalate.</li>
 *   <li>{@link #PLAYER} is the pause-menu Restart Mission path: requires matching
 *       {@code roomGeneration}. With no API activity it does not escalate; with
 *       unresolved API activity the server treats it as an API-driven abandonment
 *       (same escalation as {@link #API_STALLED}).</li>
 * </ul>
 */
public enum RoomResetReason {
	API_STALLED,
	COMBAT,
	/** Pause-menu Restart Mission — server may reclassify to escalate when API work is unresolved. */
	PLAYER
}
