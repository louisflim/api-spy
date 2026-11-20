package edu.citu.apispy.domain.rewards;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reward rules from the proposal. Regular enemy defeats give EXP, which upgrades gun attributes
 * (each gun has its own upgrade levels and EXP prices);
 * first-clear coins (10 / 20 / 30 by rank) buy new guns. Anti-farming caps limit EXP per room
 * run and per mission so replays and restarts cannot generate endless rewards.
 * Combat stats of each gun live in the game client; the server owns prices and balances.
 */
public final class RewardCatalog {

	private RewardCatalog() {
	}

	public static final String STARTER_WEAPON = "starter_sidearm";

	/**
	 * A gun in the limited weapon catalog. Price 0 means owned from the start.
	 * upgradeCosts: EXP for the next level of each attribute (index = current level).
	 */
	public record Weapon(String id, String displayName, int price, List<Integer> upgradeCosts) {
	}

	public static final List<Weapon> WEAPONS = List.of(
			new Weapon(STARTER_WEAPON, "Field Pistol", 0, List.of(30, 70, 120)),
			new Weapon("assault_rifle", "AK-47", 60, List.of(50, 110, 180)),
			new Weapon("combat_shotgun", "Shotgun", 100, List.of(60, 130, 210)));

	/**
	 * Starter characters: a new agent picks one for free; the other two cost coins.
	 * (Rank and legendary skins still unlock from mission completion, per the proposal.)
	 */
	public record Character(String id, String displayName, int price) {
	}

	public static final List<Character> CHARACTERS = List.of(
			new Character("rookie", "Rookie Detective", 50),
			new Character("agent-boy", "Agent Leo", 50),
			new Character("agent-girl", "Agent Mira", 50));

	public static Optional<Character> character(String id) {
		return CHARACTERS.stream().filter(c -> c.id().equals(id)).findFirst();
	}

	/** Gun attributes improved with EXP. */
	public enum GunStat {
		DAMAGE, FIRE_RATE, VELOCITY;

		public static Optional<GunStat> parse(String raw) {
			if (raw == null) return Optional.empty();
			try {
				return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException ex) {
				return Optional.empty();
			}
		}
	}

	/** Every attribute of every gun has three upgrade levels. */
	public static final int MAX_UPGRADE_LEVEL = 3;

	public static int maxUpgradeLevel() {
		return MAX_UPGRADE_LEVEL;
	}

	/** EXP for a regular enemy defeat at Recruit; higher ranks scale with enemy durability. */
	private static final Map<String, Integer> ENEMY_EXP = Map.of(
			"PATROL_GUARD", 5,
			"RIFLEMAN", 6,
			"SHOCK_ENFORCER", 8);

	public static final int BOSS_EXP = 40;

	/** At most this many defeats earn EXP in one room run (more than any room spawns). */
	public static final int MAX_REWARDED_DEFEATS_PER_ROOM = 12;

	/** Lifetime EXP a single mission can grant (all runs, replays included), at Recruit. */
	public static final int MISSION_EXP_CAP = 150;

	public static Optional<Integer> enemyExp(String enemyType, String rankCode) {
		if (enemyType == null) return Optional.empty();
		Integer base = ENEMY_EXP.get(enemyType.trim().toUpperCase(Locale.ROOT));
		return base == null ? Optional.empty() : Optional.of(scale(base, rankCode));
	}

	public static int bossExp(String rankCode) {
		return scale(BOSS_EXP, rankCode);
	}

	public static int missionExpCap(String rankCode) {
		return scale(MISSION_EXP_CAP, rankCode);
	}

	/** First-clear Intel Credit coins per rank (proposal: 10 Recruit, 20 Field Agent, 30 Special Agent). */
	public static int firstClearCoins(String rankCode) {
		return switch (String.valueOf(rankCode)) {
			case "FIELD_AGENT" -> 20;
			case "SPECIAL_AGENT" -> 30;
			default -> 10;
		};
	}

	private static int scale(int base, String rankCode) {
		return switch (String.valueOf(rankCode)) {
			case "FIELD_AGENT" -> (int) Math.round(base * 1.5);
			case "SPECIAL_AGENT" -> base * 2;
			default -> base;
		};
	}

	public static Optional<Weapon> weapon(String id) {
		return WEAPONS.stream().filter(w -> w.id().equals(id)).findFirst();
	}
}
