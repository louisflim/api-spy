package edu.citu.apispy.dto;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.Size;

/** A regular enemy was defeated in the active room run (EXP grant; ignored for stale rooms). */
public record EnemyDefeatRequestDto(
		@Size(max = InputLimits.ROOM_GENERATION) String roomGeneration,
		@Size(max = InputLimits.ID) String enemyType
) {
	public EnemyDefeatRequestDto {
		roomGeneration = InputSanitizer.optionalText(roomGeneration);
		enemyType = InputSanitizer.optionalText(enemyType);
	}

	/** EXP granted for this defeat and the new balance. */
	public record Result(int expAwarded, int exp) {
	}
}
