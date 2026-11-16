package edu.citu.apispy.domain.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_practice_outcomes")
public class PracticeOutcomeEntity {

	@Id
	private UUID id;

	@Column(name = "participant_code", nullable = false, length = 64)
	private String participantCode;

	@Column(name = "concept_tag", nullable = false, length = 128)
	private String conceptTag;

	@Column(name = "challenge_id", nullable = false, length = 64)
	private String challengeId;

	@Column(name = "room_mission_id", length = 64)
	private String roomMissionId;

	@Column(name = "correct", nullable = false)
	private boolean correct;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PracticeOutcomeEntity() {
	}

	public PracticeOutcomeEntity(
			UUID id,
			String participantCode,
			String conceptTag,
			String challengeId,
			String roomMissionId,
			boolean correct,
			Instant createdAt) {
		this.id = id;
		this.participantCode = participantCode;
		this.conceptTag = conceptTag;
		this.challengeId = challengeId;
		this.roomMissionId = roomMissionId;
		this.correct = correct;
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

	public String getChallengeId() {
		return challengeId;
	}

	public String getRoomMissionId() {
		return roomMissionId;
	}

	public boolean isCorrect() {
		return correct;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
