package edu.citu.apispy.dto;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.Size;

/**
 * Client report of a combat death inside an active room run.
 * <p>
 * {@code roomGeneration} must match the active run. Client-reported health is
 * <strong>not</strong> accepted as proof — the server only verifies authenticated
 * player + active room generation binding before authorizing a COMBAT reset.
 * Phaser hit events are not independently verified; that limitation is intentional
 * for Phase 1 until a stronger combat event log exists.
 */
public record CombatDeathRequestDto(
		@Size(max = InputLimits.ROOM_GENERATION) String roomGeneration
) {
	public CombatDeathRequestDto {
		roomGeneration = InputSanitizer.optionalText(roomGeneration);
	}
}
