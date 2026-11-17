package edu.citu.apispy.dto;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.Size;

/**
 * Client report that the Level 7 boss was defeated in the active room run.
 * <p>
 * {@code roomGeneration} must match the active run, and the server rejects the report
 * while any mandatory API device is still locked (boss shield up). Phaser hits are not
 * independently verified, the same limitation as {@link CombatDeathRequestDto}.
 */
public record BossDefeatRequestDto(
		@Size(max = InputLimits.ROOM_GENERATION) String roomGeneration
) {
	public BossDefeatRequestDto {
		roomGeneration = InputSanitizer.optionalText(roomGeneration);
	}
}
