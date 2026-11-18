package edu.citu.apispy.dto;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.Size;

/**
 * Client signal for the hidden research log (room entered / terminal opened).
 * Bound to the active {@code roomGeneration}; stale or foreign signals are ignored server-side.
 */
public record ResearchSignalDto(
		@Size(max = InputLimits.ROOM_GENERATION) String roomGeneration,
		@Size(max = InputLimits.ID) String deviceId
) {
	public ResearchSignalDto {
		roomGeneration = InputSanitizer.optionalText(roomGeneration);
		deviceId = InputSanitizer.optionalText(deviceId);
	}
}
