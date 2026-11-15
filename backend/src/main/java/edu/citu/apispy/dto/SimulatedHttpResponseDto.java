package edu.citu.apispy.dto;

import java.util.Map;

public record SimulatedHttpResponseDto(
		int status,
		Map<String, String> headers,
		Object body
) {
}
