package edu.citu.apispy.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Room reset request. {@code reason} selects the reset policy.
 * {@code roomGeneration} is required for {@code PLAYER} (pause-menu Reset level)
 * so stale clients cannot reset a superseded room instance.
 */
public record RoomResetRequestDto(
		@NotBlank String reason,
		String roomGeneration
) {
}
