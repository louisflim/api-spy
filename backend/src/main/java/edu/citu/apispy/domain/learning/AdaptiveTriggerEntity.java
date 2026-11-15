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
		name = "learning_adaptive_triggers",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_learning_adaptive_trigger_evidence",
				columnNames = { "participant_code", "concept_tag", "evidence_key" }))
public class AdaptiveTriggerEntity {

	@Id
	private UUID id;

	@Column(name = "participant_code", nullable = false, length = 64)
	private String participantCode;

	@Column(name = "concept_tag", nullable = false, length = 128)
	private String conceptTag;

	@Column(name = "room_mission_id", nullable = false, length = 64)
	private String roomMissionId;

	@Column(name = "evidence_key", nullable = false, length = 256)
	private String evidenceKey;

	@Column(name = "practice_challenge_id", nullable = false, length = 64)
	private String practiceChallengeId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AdaptiveTriggerEntity() {
	}

	public AdaptiveTriggerEntity(
			UUID id,
			String participantCode,
			String conceptTag,
			String roomMissionId,
			String evidenceKey,
			String practiceChallengeId,
			Instant createdAt) {
		this.id = id;
		this.participantCode = participantCode;
		this.conceptTag = conceptTag;
		this.roomMissionId = roomMissionId;
		this.evidenceKey = evidenceKey;
		this.practiceChallengeId = practiceChallengeId;
		this.createdAt = createdAt;
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

	public String getRoomMissionId() {
		return roomMissionId;
	}

	public String getEvidenceKey() {
		return evidenceKey;
	}

	public String getPracticeChallengeId() {
		return practiceChallengeId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
