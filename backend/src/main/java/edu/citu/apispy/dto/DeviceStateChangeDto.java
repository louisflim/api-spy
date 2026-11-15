package edu.citu.apispy.dto;

public record DeviceStateChangeDto(
		String deviceId,
		String state,
		boolean unlocked
) {
}
