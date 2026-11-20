package edu.citu.apispy.domain.rewards;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One agent's EXP and coin balances, per-gun upgrade levels and owned/equipped guns. */
@Entity
@Table(name = "agent_rewards")
public class AgentRewardsEntity {

	@Id
	@Column(name = "participant_code", length = 64)
	private String participantCode;

	@Column(nullable = false)
	private int exp;

	@Column(name = "exp_earned", nullable = false)
	private int expEarned;

	@Column(nullable = false)
	private int coins;

	@Column(name = "coins_earned", nullable = false)
	private int coinsEarned;

	/** Per-gun upgrade levels: "weaponId:damage:fireRate:velocity" entries, comma-separated. */
	@Column(name = "gun_upgrades", nullable = false, length = 512)
	private String gunUpgrades;

	@Column(name = "owned_weapons", nullable = false, length = 256)
	private String ownedWeapons;

	@Column(name = "equipped_weapon", nullable = false, length = 64)
	private String equippedWeapon;

	@Column(name = "owned_characters", nullable = false, length = 256)
	private String ownedCharacters;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected AgentRewardsEntity() {
	}

	public static AgentRewardsEntity fresh(String participantCode) {
		AgentRewardsEntity e = new AgentRewardsEntity();
		e.participantCode = participantCode;
		e.ownedWeapons = RewardCatalog.STARTER_WEAPON;
		e.equippedWeapon = RewardCatalog.STARTER_WEAPON;
		e.ownedCharacters = "";
		e.gunUpgrades = "";
		e.updatedAt = Instant.now();
		return e;
	}

	/** Starter characters this agent owns (empty until the free sign-up pick). */
	public List<String> ownedCharacters() {
		if (ownedCharacters == null) return List.of();
		return Arrays.stream(ownedCharacters.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
	}

	public boolean ownsCharacter(String characterId) {
		return ownedCharacters().contains(characterId);
	}

	public void addCharacter(String characterId) {
		if (ownsCharacter(characterId)) return;
		List<String> next = new java.util.ArrayList<>(ownedCharacters());
		next.add(characterId);
		ownedCharacters = String.join(",", next);
		touch();
	}

	public void addExp(int amount) {
		exp += amount;
		if (amount > 0) expEarned += amount;
		touch();
	}

	public void addCoins(int amount) {
		coins += amount;
		if (amount > 0) coinsEarned += amount;
		touch();
	}

	/** Upgrade levels of one gun, in {@link RewardCatalog.GunStat} order (0 when never upgraded). */
	private Map<String, int[]> upgradeTable() {
		Map<String, int[]> table = new LinkedHashMap<>();
		if (gunUpgrades == null || gunUpgrades.isBlank()) return table;
		for (String entry : gunUpgrades.split(",")) {
			String[] parts = entry.trim().split(":");
			if (parts.length != 4 || parts[0].isBlank()) continue;
			int[] levels = new int[3];
			for (int i = 0; i < 3; i++) {
				try {
					levels[i] = Math.max(0, Math.min(RewardCatalog.MAX_UPGRADE_LEVEL, Integer.parseInt(parts[i + 1].trim())));
				} catch (NumberFormatException ex) {
					levels[i] = 0;
				}
			}
			table.put(parts[0].trim(), levels);
		}
		return table;
	}

	public int level(String weaponId, RewardCatalog.GunStat stat) {
		int[] levels = upgradeTable().get(weaponId);
		return levels == null ? 0 : levels[stat.ordinal()];
	}

	public void setLevel(String weaponId, RewardCatalog.GunStat stat, int level) {
		Map<String, int[]> table = upgradeTable();
		int[] levels = table.computeIfAbsent(weaponId, k -> new int[3]);
		levels[stat.ordinal()] = level;
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, int[]> e : table.entrySet()) {
			if (sb.length() > 0) sb.append(',');
			int[] l = e.getValue();
			sb.append(e.getKey()).append(':').append(l[0]).append(':').append(l[1]).append(':').append(l[2]);
		}
		gunUpgrades = sb.toString();
		touch();
	}

	public List<String> ownedWeapons() {
		Set<String> owned = new LinkedHashSet<>();
		owned.add(RewardCatalog.STARTER_WEAPON);
		Arrays.stream(ownedWeapons.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(owned::add);
		return List.copyOf(owned);
	}

	public boolean owns(String weaponId) {
		return ownedWeapons().contains(weaponId);
	}

	public void addWeapon(String weaponId) {
		if (owns(weaponId)) return;
		ownedWeapons = String.join(",", ownedWeapons()) + "," + weaponId;
		touch();
	}

	public void equip(String weaponId) {
		equippedWeapon = weaponId;
		touch();
	}

	private void touch() {
		updatedAt = Instant.now();
	}

	public String getParticipantCode() {
		return participantCode;
	}

	public int getExp() {
		return exp;
	}

	public int getExpEarned() {
		return expEarned;
	}

	public int getCoins() {
		return coins;
	}

	public int getCoinsEarned() {
		return coinsEarned;
	}

	public String getEquippedWeapon() {
		return equippedWeapon;
	}
}
