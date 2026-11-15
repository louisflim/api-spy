package edu.citu.apispy.domain.simulation;

/**
 * Separates graded missions from voluntary practice and Module 0 training.
 * Enforced server-side in {@code RequestSimulationService}.
 */
public enum SimulationMode {
	/** Graded independent mission — first attempts and device unlocks apply. */
	MISSION,
	/** Voluntary Operations Board practice — educational only. */
	PRACTICE,
	/** Module 0 terminal field training — educational only. */
	TRAINING;

	public boolean isGraded() {
		return this == MISSION;
	}

	public static SimulationMode fromNullable(String raw) {
		if (raw == null || raw.isBlank()) {
			return MISSION;
		}
		return SimulationMode.valueOf(raw.trim().toUpperCase());
	}
}
