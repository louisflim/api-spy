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
		name = "learning_terminal_verification",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_learning_terminal_verification",
				columnNames = { "participant_code", "mission_id", "challenge_id", "concept_tag" }))
public class TerminalVerificationEvidenceEntity {

	@Id
	private UUID id;

	@Column(name = "participant_code", nullable = false, length = 64)
	private String participantCode;

	@Column(name = "mission_id", nullable = false, length = 64)
	private String missionId;

	@Column(name = "terminal_id", nullable = false, length = 64)
	private String terminalId;

	@Column(name = "challenge_id", nullable = false, length = 64)
	private String challengeId;

	@Column(name = "concept_tag", nullable = false, length = 128)
	private String conceptTag;

	@Column(name = "earned_at", nullable = false)
	private Instant earnedAt;

	protected TerminalVerificationEvidenceEntity() {
	}

	public TerminalVerificationEvidenceEntity(
			UUID id,
			String participantCode,
			String missionId,
			String terminalId,
			String challengeId,
			String conceptTag,
			Instant earnedAt) {
		this.id = id;
		this.participantCode = participantCode;
		this.missionId = missionId;
		this.terminalId = terminalId;
		this.challengeId = challengeId;
		this.conceptTag = conceptTag;
		this.earnedAt = earnedAt;
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

	public String getTerminalId() {
		return terminalId;
	}

	public String getChallengeId() {
		return challengeId;
	}

	public String getConceptTag() {
		return conceptTag;
	}

	public Instant getEarnedAt() {
		return earnedAt;
	}
}
