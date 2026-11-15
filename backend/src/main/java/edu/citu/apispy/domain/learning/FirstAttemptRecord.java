package edu.citu.apispy.domain.learning;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable first-submission record for an eligible mandatory decision.
 * Compatible with a future Supabase/PostgreSQL row shape.
 */
public final class FirstAttemptRecord {

	private final String participantCode;
	private final String missionId;
	private final String deviceId;
	private final String challengeId;
	private final String variantId;
	private final List<String> conceptTags;
	private final String method;
	private final String path;
	private final Map<String, String> headers;
	private final String body;
	private final List<String> errorCodes;
	private final boolean firstAttemptCorrect;
	private final Map<String, Boolean> conceptFirstAttemptCorrect;
	private final Instant submittedAt;

	public FirstAttemptRecord(
			String participantCode,
			String missionId,
			String deviceId,
			String challengeId,
			String variantId,
			List<String> conceptTags,
			String method,
			String path,
			Map<String, String> headers,
			String body,
			List<String> errorCodes,
			boolean firstAttemptCorrect,
			Map<String, Boolean> conceptFirstAttemptCorrect,
			Instant submittedAt) {
		this.participantCode = Objects.requireNonNull(participantCode);
		this.missionId = Objects.requireNonNull(missionId);
		this.deviceId = Objects.requireNonNull(deviceId);
		this.challengeId = Objects.requireNonNull(challengeId);
		this.variantId = Objects.requireNonNull(variantId);
		this.conceptTags = List.copyOf(conceptTags);
		this.method = method;
		this.path = path;
		this.headers = headers == null ? Map.of() : Map.copyOf(headers);
		this.body = body == null ? "" : body;
		this.errorCodes = List.copyOf(errorCodes);
		this.firstAttemptCorrect = firstAttemptCorrect;
		this.conceptFirstAttemptCorrect = Map.copyOf(conceptFirstAttemptCorrect);
		this.submittedAt = Objects.requireNonNull(submittedAt);
	}

	public String getParticipantCode() {
		return participantCode;
	}

	public String getMissionId() {
		return missionId;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public String getChallengeId() {
		return challengeId;
	}

	public String getVariantId() {
		return variantId;
	}

	public List<String> getConceptTags() {
		return conceptTags;
	}

	public String getMethod() {
		return method;
	}

	public String getPath() {
		return path;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public String getBody() {
		return body;
	}

	public List<String> getErrorCodes() {
		return errorCodes;
	}

	public boolean isFirstAttemptCorrect() {
		return firstAttemptCorrect;
	}

	public Map<String, Boolean> getConceptFirstAttemptCorrect() {
		return conceptFirstAttemptCorrect;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public static String key(String participantCode, String missionId, String deviceId) {
		return participantCode + "|" + missionId + "|" + deviceId;
	}
}
