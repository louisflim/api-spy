package edu.citu.apispy.dto;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;
import java.util.Map;

/**
 * Transport DTO for React → Spring Boot. Student method/path are data fields only.
 * {@code mode} defaults to MISSION when omitted (graded independent missions).
 */
public record SimulateRequestDto(
		@NotBlank @Size(max = InputLimits.ID) String participantCode,
		@NotBlank @Size(max = InputLimits.ID) String missionId,
		@NotBlank @Size(max = InputLimits.ID) String deviceId,
		@Size(max = InputLimits.ID) String challengeId,
		@NotBlank @Size(max = InputLimits.HTTP_METHOD)
		@Pattern(regexp = "^[A-Za-z]{1,16}$", message = "must be an HTTP method")
		String method,
		@NotBlank @Size(max = InputLimits.PATH) String path,
		Map<String, String> headers,
		@Size(max = InputLimits.BODY) String body,
		@Size(max = 16)
		@Pattern(regexp = "^(?i)(MISSION|PRACTICE|TRAINING)$", message = "must be MISSION, PRACTICE, or TRAINING")
		String mode
) {
	public SimulateRequestDto {
		participantCode = InputSanitizer.id(participantCode);
		missionId = InputSanitizer.id(missionId);
		deviceId = InputSanitizer.id(deviceId);
		challengeId = InputSanitizer.optionalText(challengeId);
		method = InputSanitizer.text(method);
		if (method != null) {
			method = method.toUpperCase(Locale.ROOT);
		}
		path = InputSanitizer.text(path);
		headers = InputSanitizer.headers(headers);
		body = InputSanitizer.body(body);
		if (mode == null || mode.isBlank()) {
			mode = "MISSION";
		} else {
			mode = InputSanitizer.text(mode).toUpperCase(Locale.ROOT);
		}
	}
}
