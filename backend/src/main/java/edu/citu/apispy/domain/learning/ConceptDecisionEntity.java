package edu.citu.apispy.domain.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_concept_decisions")
public class ConceptDecisionEntity {

	@Id
	private UUID id;

	@Column(name = "participant_code", nullable = false, length = 64)
	private String participantCode;

	@Column(name = "mission_id", nullable = false, length = 64)
	private String missionId;

	@Column(name = "rank_code", nullable = false, length = 32)
	private String rankCode;

	@Column(name = "challenge_id", nullable = false, length = 64)
	private String challengeId;

	@Column(name = "device_id", nullable = false, length = 64)
	private String deviceId;

	@Column(name = "concept_tag", nullable = false, length = 128)
	private String conceptTag;

	@Column(nullable = false)
	private boolean applicable;

	@Column(nullable = false)
	private boolean correct;

	@Column(name = "error_codes_json", nullable = false, columnDefinition = "TEXT")
	private String errorCodesJson;

	@Column(name = "submission_hash", nullable = false, length = 128)
	private String submissionHash;

	@Column(name = "first_eligible", nullable = false)
	private boolean firstEligible;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ConceptDecisionEntity() {
	}

	public ConceptDecisionEntity(
			UUID id,
			String participantCode,
			String missionId,
			String rankCode,
			String challengeId,
			String deviceId,
			String conceptTag,
			boolean applicable,
			boolean correct,
			String errorCodesJson,
			String submissionHash,
			boolean firstEligible,
			Instant createdAt) {
		this.id = id;
		this.participantCode = participantCode;
		this.missionId = missionId;
		this.rankCode = rankCode;
		this.challengeId = challengeId;
		this.deviceId = deviceId;
		this.conceptTag = conceptTag;
		this.applicable = applicable;
		this.correct = correct;
		this.errorCodesJson = errorCodesJson;
		this.submissionHash = submissionHash;
		this.firstEligible = firstEligible;
		this.createdAt = createdAt;
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

	public String getConceptTag() {
		return conceptTag;
	}

	public boolean isApplicable() {
		return applicable;
	}

	public boolean isCorrect() {
		return correct;
	}

	public boolean isFirstEligible() {
		return firstEligible;
	}
}
