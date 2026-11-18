package edu.citu.apispy.domain.research;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "research_events")
public class ResearchEventEntity {

	@Id
	private UUID id;

	@Column(name = "participant_code", nullable = false, length = 64)
	private String participantCode;

	@Column(name = "mission_id", nullable = false, length = 64)
	private String missionId;

	@Column(nullable = false)
	private int module;

	@Column(name = "rank_code", nullable = false, length = 32)
	private String rankCode;

	@Column(nullable = false)
	private int level;

	@Column(name = "room_generation")
	private UUID roomGeneration;

	@Column(name = "event_type", nullable = false, length = 32)
	private String eventType;

	@Column(length = 32)
	private String reason;

	@Column(name = "device_id", length = 64)
	private String deviceId;

	@Column(name = "run_kind", length = 16)
	private String runKind;

	@Column(name = "api_reset_count")
	private Integer apiResetCount;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected ResearchEventEntity() {
	}

	public static ResearchEventEntity from(ResearchEvent e) {
		ResearchEventEntity entity = new ResearchEventEntity();
		entity.id = e.id();
		entity.participantCode = e.participantCode();
		entity.missionId = e.missionId();
		entity.module = e.module();
		entity.rankCode = e.rank();
		entity.level = e.level();
		entity.roomGeneration = e.roomGeneration();
		entity.eventType = e.type();
		entity.reason = e.reason();
		entity.deviceId = e.deviceId();
		entity.runKind = e.runKind();
		entity.apiResetCount = e.apiResetCount();
		entity.occurredAt = e.occurredAt();
		return entity;
	}

	public ResearchEvent toEvent() {
		return new ResearchEvent(id, participantCode, missionId, module, rankCode, level, roomGeneration,
				eventType, reason, deviceId, runKind, apiResetCount, occurredAt);
	}
}
