package edu.citu.apispy.domain.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_concept_consistency")
public class ConceptConsistencyEntity {

	@Id
	private UUID id;

	@Column(name = "participant_code", nullable = false, length = 64)
	private String participantCode;

	@Column(name = "concept_tag", nullable = false, length = 128)
	private String conceptTag;

	@Column(name = "mission_id", nullable = false, length = 64)
	private String missionId;

	@Column(name = "source_challenge_id", nullable = false, length = 64)
	private String sourceChallengeId;

	@Column(name = "consecutive_successes", nullable = false)
	private int consecutiveSuccesses;

	@Column(name = "scenarios_used", nullable = false)
	private int scenariosUsed;

	@Column(name = "observed_challenge_ids_json", nullable = false)
	private String observedChallengeIdsJson;

	@Column(name = "planned_challenge_ids_json", nullable = false)
	private String plannedChallengeIdsJson;

	@Column(nullable = false, length = 32)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ConceptConsistencyEntity() {
	}

	public ConceptConsistencyEntity(
			UUID id,
			String participantCode,
			String conceptTag,
			String missionId,
			String sourceChallengeId,
			int consecutiveSuccesses,
			int scenariosUsed,
			String observedChallengeIdsJson,
			String plannedChallengeIdsJson,
			String status,
			Instant createdAt,
			Instant updatedAt) {
		this.id = id;
		this.participantCode = participantCode;
		this.conceptTag = conceptTag;
		this.missionId = missionId;
		this.sourceChallengeId = sourceChallengeId;
		this.consecutiveSuccesses = consecutiveSuccesses;
		this.scenariosUsed = scenariosUsed;
		this.observedChallengeIdsJson = observedChallengeIdsJson;
		this.plannedChallengeIdsJson = plannedChallengeIdsJson;
		this.status = status;
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

	public String getMissionId() {
		return missionId;
	}

	public String getSourceChallengeId() {
		return sourceChallengeId;
	}

	public int getConsecutiveSuccesses() {
		return consecutiveSuccesses;
	}

	public void setConsecutiveSuccesses(int consecutiveSuccesses) {
		this.consecutiveSuccesses = consecutiveSuccesses;
	}

	public int getScenariosUsed() {
		return scenariosUsed;
	}

	public void setScenariosUsed(int scenariosUsed) {
		this.scenariosUsed = scenariosUsed;
	}

	public String getObservedChallengeIdsJson() {
		return observedChallengeIdsJson;
	}

	public void setObservedChallengeIdsJson(String observedChallengeIdsJson) {
		this.observedChallengeIdsJson = observedChallengeIdsJson;
	}

	public String getPlannedChallengeIdsJson() {
		return plannedChallengeIdsJson;
	}

	public void setPlannedChallengeIdsJson(String plannedChallengeIdsJson) {
		this.plannedChallengeIdsJson = plannedChallengeIdsJson;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
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
