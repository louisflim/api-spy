package edu.citu.apispy.domain.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
		name = "learning_first_attempts",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_learning_first_attempt",
				columnNames = { "participant_code", "mission_id", "device_id" }))
public class FirstAttemptEntity {

	@Id
	private UUID id;

	@Column(name = "participant_code", nullable = false, length = 64)
	private String participantCode;

	@Column(name = "mission_id", nullable = false, length = 64)
	private String missionId;

	@Column(name = "device_id", nullable = false, length = 64)
	private String deviceId;

	@Column(name = "challenge_id", nullable = false, length = 64)
	private String challengeId;

	@Column(name = "variant_id", nullable = false, length = 64)
	private String variantId;

	@Column(name = "concept_tags_json", nullable = false, columnDefinition = "TEXT")
	private String conceptTagsJson;

	@Column(nullable = false, length = 16)
	private String method;

	@Column(nullable = false, length = 512)
	private String path;

	@Column(name = "headers_json", nullable = false, columnDefinition = "TEXT")
	private String headersJson;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;

	@Column(name = "error_codes_json", nullable = false, columnDefinition = "TEXT")
	private String errorCodesJson;

	@Column(name = "first_attempt_correct", nullable = false)
	private boolean firstAttemptCorrect;

	@Column(name = "concept_correct_json", nullable = false, columnDefinition = "TEXT")
	private String conceptCorrectJson;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	protected FirstAttemptEntity() {
	}

	public FirstAttemptEntity(
			UUID id,
			String participantCode,
			String missionId,
			String deviceId,
			String challengeId,
			String variantId,
			String conceptTagsJson,
			String method,
			String path,
			String headersJson,
			String body,
			String errorCodesJson,
			boolean firstAttemptCorrect,
			String conceptCorrectJson,
			Instant submittedAt) {
		this.id = id;
		this.participantCode = participantCode;
		this.missionId = missionId;
		this.deviceId = deviceId;
		this.challengeId = challengeId;
		this.variantId = variantId;
		this.conceptTagsJson = conceptTagsJson;
		this.method = method;
		this.path = path;
		this.headersJson = headersJson;
		this.body = body;
		this.errorCodesJson = errorCodesJson;
		this.firstAttemptCorrect = firstAttemptCorrect;
		this.conceptCorrectJson = conceptCorrectJson;
		this.submittedAt = submittedAt;
	}

	public UUID getId() {
		return id;
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

	public String getConceptTagsJson() {
		return conceptTagsJson;
	}

	public String getMethod() {
		return method;
	}

	public String getPath() {
		return path;
	}

	public String getHeadersJson() {
		return headersJson;
	}

	public String getBody() {
		return body;
	}

	public String getErrorCodesJson() {
		return errorCodesJson;
	}

	public boolean isFirstAttemptCorrect() {
		return firstAttemptCorrect;
	}

	public String getConceptCorrectJson() {
		return conceptCorrectJson;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}
}
