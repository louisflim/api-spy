package edu.citu.apispy.domain.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_concept_review_queue")
public class ConceptReviewEntity {

	@Id
	private UUID id;

	@Column(name = "participant_code", nullable = false, length = 64)
	private String participantCode;

	@Column(name = "concept_tag", nullable = false, length = 128)
	private String conceptTag;

	@Column(name = "source_mission_id", nullable = false, length = 64)
	private String sourceMissionId;

	@Column(nullable = false, length = 32)
	private String status;

	@Column(name = "review_challenge_id", length = 64)
	private String reviewChallengeId;

	@Column(name = "outcome_correct")
	private Boolean outcomeCorrect;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ConceptReviewEntity() {
	}

	public ConceptReviewEntity(
			UUID id,
			String participantCode,
			String conceptTag,
			String sourceMissionId,
			String status,
			String reviewChallengeId,
			Boolean outcomeCorrect,
			Instant createdAt,
			Instant updatedAt) {
		this.id = id;
		this.participantCode = participantCode;
		this.conceptTag = conceptTag;
		this.sourceMissionId = sourceMissionId;
		this.status = status;
		this.reviewChallengeId = reviewChallengeId;
		this.outcomeCorrect = outcomeCorrect;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public UUID getId() {
		return id;
	}

	public String getParticipantCode() {
		return participantCode;
	}

	public String getConceptTag() {
		return conceptTag;
	}

	public String getSourceMissionId() {
		return sourceMissionId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getReviewChallengeId() {
		return reviewChallengeId;
	}

	public void setReviewChallengeId(String reviewChallengeId) {
		this.reviewChallengeId = reviewChallengeId;
	}

	public Boolean getOutcomeCorrect() {
		return outcomeCorrect;
	}

	public void setOutcomeCorrect(Boolean outcomeCorrect) {
		this.outcomeCorrect = outcomeCorrect;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
