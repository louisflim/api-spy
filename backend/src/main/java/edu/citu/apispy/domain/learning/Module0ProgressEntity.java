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
		name = "module0_orientation_progress",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_module0_orientation_participant",
				columnNames = { "participant_code" }))
public class Module0ProgressEntity {

	@Id
	private UUID id;

	@Column(name = "participant_code", nullable = false, length = 64)
	private String participantCode;

	@Column(name = "last_topic_id", nullable = false, length = 64)
	private String lastTopicId;

	@Column(name = "completed_topic_ids_json", nullable = false, columnDefinition = "TEXT")
	private String completedTopicIdsJson;

	@Column(name = "completed_steps_json", nullable = false, columnDefinition = "TEXT")
	private String completedStepsJson;

	@Column(name = "orientation_completed", nullable = false)
	private boolean orientationCompleted;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Module0ProgressEntity() {
	}

	public Module0ProgressEntity(
			UUID id,
			String participantCode,
			String lastTopicId,
			String completedTopicIdsJson,
			String completedStepsJson,
			boolean orientationCompleted,
			Instant updatedAt) {
		this.id = id;
		this.participantCode = participantCode;
		this.lastTopicId = lastTopicId;
		this.completedTopicIdsJson = completedTopicIdsJson;
		this.completedStepsJson = completedStepsJson;
		this.orientationCompleted = orientationCompleted;
		this.updatedAt = updatedAt;
	}

	public UUID getId() {
		return id;
	}

	public String getParticipantCode() {
		return participantCode;
	}

	public String getLastTopicId() {
		return lastTopicId;
	}

	public void setLastTopicId(String lastTopicId) {
		this.lastTopicId = lastTopicId;
	}

	public String getCompletedTopicIdsJson() {
		return completedTopicIdsJson;
	}

	public void setCompletedTopicIdsJson(String completedTopicIdsJson) {
		this.completedTopicIdsJson = completedTopicIdsJson;
	}

	public String getCompletedStepsJson() {
		return completedStepsJson;
	}

	public void setCompletedStepsJson(String completedStepsJson) {
		this.completedStepsJson = completedStepsJson;
	}

	public boolean isOrientationCompleted() {
		return orientationCompleted;
	}

	public void setOrientationCompleted(boolean orientationCompleted) {
		this.orientationCompleted = orientationCompleted;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
