package edu.citu.apispy.dto;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.Size;

/**
 * Completion request bound to the active room instance.
 * <p>
 * {@code roomGeneration} must match the server-issued id from mission progress.
 * {@code exitInteracted} is a client gameplay signal only — it does <strong>not</strong>
 * prove Phaser physical position. Authoritative checks are: authenticated player,
 * active {@code IN_PROGRESS} mission, all mandatory devices unlocked, and matching
 * {@code roomGeneration}. Physical exit interaction remains required in normal
 * client gameplay; the server cannot yet verify sprite coordinates.
 */
public record MissionCompleteRequestDto(
		@Size(max = InputLimits.ROOM_GENERATION) String roomGeneration,
		Boolean exitInteracted
) {
	public MissionCompleteRequestDto {
		roomGeneration = InputSanitizer.optionalText(roomGeneration);
	}
}
