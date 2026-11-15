package edu.citu.apispy.domain.simulation;

import java.util.Objects;

/**
 * Environmental device state update produced by a successful simulated request.
 */
public final class DeviceStateChange {

	private final String deviceId;
	private final String state;
	private final boolean unlocked;

	public DeviceStateChange(String deviceId, String state, boolean unlocked) {
		this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
		this.state = Objects.requireNonNull(state, "state");
		this.unlocked = unlocked;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public String getState() {
		return state;
	}

	public boolean isUnlocked() {
		return unlocked;
	}
}
