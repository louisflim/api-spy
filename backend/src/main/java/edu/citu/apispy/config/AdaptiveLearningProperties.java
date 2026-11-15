package edu.citu.apispy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable adaptive-practice thresholds.
 * Separate from the 1 → 3 → 5 API-device escalation mechanic.
 */
@ConfigurationProperties(prefix = "apispy.adaptive")
public class AdaptiveLearningProperties {

	private int incorrectAttemptThreshold = 2;
	private int maxPracticeDoorsPerRoom = 2;

	public int getIncorrectAttemptThreshold() {
		return incorrectAttemptThreshold;
	}

	public void setIncorrectAttemptThreshold(int incorrectAttemptThreshold) {
		this.incorrectAttemptThreshold = incorrectAttemptThreshold;
	}

	public int getMaxPracticeDoorsPerRoom() {
		return maxPracticeDoorsPerRoom;
	}

	public void setMaxPracticeDoorsPerRoom(int maxPracticeDoorsPerRoom) {
		this.maxPracticeDoorsPerRoom = maxPracticeDoorsPerRoom;
	}
}
