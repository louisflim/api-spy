package edu.citu.apispy.domain.mission;

/**
 * Separates the active temporary room attempt from permanent career completion.
 * <ul>
 *   <li>{@link #PRIMARY} — first-clear field run ({@code IN_PROGRESS}).</li>
 *   <li>{@link #REPLAY} — authorized post-clear practice run; career stays {@code COMPLETED}.</li>
 *   <li>{@link #NONE} — no active room run (typical after career clear, before replay).</li>
 * </ul>
 */
public enum ActiveRunKind {
	NONE,
	PRIMARY,
	REPLAY
}
