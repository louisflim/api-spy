package edu.citu.apispy.domain.rewards;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only record of one reward grant (positive amount) or spend (negative amount). */
@Entity
@Table(name = "reward_ledger")
public class RewardLedgerEntity {

	public static final String ENEMY_EXP = "ENEMY_EXP";
	public static final String BOSS_EXP = "BOSS_EXP";
	public static final String FIRST_CLEAR_COINS = "FIRST_CLEAR_COINS";
	public static final String GUN_UPGRADE = "GUN_UPGRADE";
	public static final String GUN_PURCHASE = "GUN_PURCHASE";
	public static final String STARTER_CHARACTER = "STARTER_CHARACTER";
	public static final String CHARACTER_PURCHASE = "CHARACTER_PURCHASE";

	public static final String EXP = "EXP";
	public static final String COINS = "COINS";

	@Id
	private UUID id;

	@Column(name = "participant_code", nullable = false, length = 64)
	private String participantCode;

	@Column(nullable = false, length = 32)
	private String kind;

	@Column(nullable = false, length = 8)
	private String currency;

	@Column(nullable = false)
	private int amount;

	@Column(name = "mission_id", length = 64)
	private String missionId;

	@Column(name = "room_generation")
	private UUID roomGeneration;

	@Column(length = 64)
	private String detail;

	@Column(name = "dedupe_key", length = 160, unique = true)
	private String dedupeKey;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected RewardLedgerEntity() {
	}

	public static RewardLedgerEntity of(
			String participantCode, String kind, String currency, int amount,
			String missionId, UUID roomGeneration, String detail, String dedupeKey) {
		RewardLedgerEntity e = new RewardLedgerEntity();
		e.id = UUID.randomUUID();
		e.participantCode = participantCode;
		e.kind = kind;
		e.currency = currency;
		e.amount = amount;
		e.missionId = missionId;
		e.roomGeneration = roomGeneration;
		e.detail = detail;
		e.dedupeKey = dedupeKey;
		e.occurredAt = Instant.now();
		return e;
	}

	public String getKind() {
		return kind;
	}

	public int getAmount() {
		return amount;
	}

	public String getDetail() {
		return detail;
	}
}
