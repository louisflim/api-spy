package edu.citu.apispy.domain.learning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
		name = "learning_concept_progress",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_learning_concept_progress",
				columnNames = { "participant_code", "concept_id" }))
public class ConceptProgressEntity {

	@Id
	private UUID id;

	@Column(name = "participant_code", nullable = false, length = 64)
	private String participantCode;

	@Column(name = "concept_id", nullable = false, length = 128)
	private String conceptId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ConceptStage stage;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ConceptProgressEntity() {
	}

	public ConceptProgressEntity(
			UUID id,
			String participantCode,
			String conceptId,
			ConceptStage stage,
			Instant updatedAt) {
		this.id = id;
		this.participantCode = participantCode;
		this.conceptId = conceptId;
		this.stage = stage;
		this.updatedAt = updatedAt;
	}

	public UUID getId() {
		return id;
	}

	public String getParticipantCode() {
		return participantCode;
	}

	public String getConceptId() {
		return conceptId;
	}

	public ConceptStage getStage() {
		return stage;
	}

	public void setStage(ConceptStage stage) {
		this.stage = stage;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
