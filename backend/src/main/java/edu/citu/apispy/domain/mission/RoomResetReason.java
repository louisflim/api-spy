package edu.citu.apispy.domain.mission;

/**
 * Distinguishes API-driven room resets from combat-only and voluntary restarts (SRS).
 * Combat-only and player restarts must not change {@code apiResetCount} (1→3→5 tier).
 * <p>
 * Clients cannot freely choose gated reasons:
 * <ul>
 *   <li>{@link #API_STALLED} requires server-tracked graded activity since the last reset;
 *       increments {@code apiResetCount}.</li>
 *   <li>{@link #COMBAT} requires a prior server-side combat-death authorization flag;
 *       does not escalate.</li>
 *   <li>{@link #PLAYER} is the pause-menu Reset level path: requires matching
 *       {@code roomGeneration}, does not escalate, preserves {@link ActiveRunKind}.</li>
 * </ul>
 */
public enum RoomResetReason {
	API_STALLED,
	COMBAT,
	/** Voluntary level restart from the field pause menu — no API tier escalation. */
	PLAYER
}
