package edu.citu.apispy.dto;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Room reset request. {@code reason} selects the reset policy.
 * {@code roomGeneration} is required for {@code PLAYER} (pause-menu Reset level)
 * so stale clients cannot reset a superseded room instance.
 */
public record RoomResetRequestDto(
		@NotBlank @Size(max = InputLimits.RESET_REASON) String reason,
		@Size(max = InputLimits.ROOM_GENERATION) String roomGeneration
) {
	public RoomResetRequestDto {
		reason = InputSanitizer.text(reason);
		roomGeneration = InputSanitizer.optionalText(roomGeneration);
	}
}
