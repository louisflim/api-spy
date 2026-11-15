package edu.citu.apispy.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Transport DTO for React → Spring Boot. Student method/path are data fields only.
 * {@code mode} defaults to MISSION when omitted (graded independent missions).
 */
public record SimulateRequestDto(
		@NotBlank String participantCode,
		@NotBlank String missionId,
		@NotBlank String deviceId,
		String challengeId,
		@NotBlank String method,
		@NotBlank String path,
		Map<String, String> headers,
		String body,
		String mode
) {
	public SimulateRequestDto {
		if (mode == null || mode.isBlank()) {
			mode = "MISSION";
		}
	}
}
