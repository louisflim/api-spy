package edu.citu.apispy.service.rewards;

import edu.citu.apispy.domain.rewards.AgentRewardsEntity;
import edu.citu.apispy.domain.rewards.RewardCatalog;
import edu.citu.apispy.domain.rewards.RewardCatalog.GunStat;
import edu.citu.apispy.domain.rewards.RewardLedgerEntity;
import edu.citu.apispy.dto.RewardsDto;
import edu.citu.apispy.repository.AgentRewardsRepository;
import edu.citu.apispy.repository.RewardLedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet, grants and the gun shop. Every change is written to the reward ledger.
 * Rewards are gameplay only: they never touch API validation, devices or research KPIs.
 */
@Service
public class RewardService implements RewardHooks {

	private static final List<String> EXP_GRANTS = List.of(RewardLedgerEntity.ENEMY_EXP, RewardLedgerEntity.BOSS_EXP);

	private final AgentRewardsRepository wallets;
	private final RewardLedgerRepository ledger;

	public RewardService(AgentRewardsRepository wallets, RewardLedgerRepository ledger) {
		this.wallets = wallets;
		this.ledger = ledger;
	}

	@Transactional
	public RewardsDto get(String participantCode) {
		return toDto(lockedWallet(participantCode));
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public int enemyDefeated(String participantCode, String missionId, String rankCode, UUID roomGeneration, String enemyType) {
		var exp = RewardCatalog.enemyExp(enemyType, rankCode);
		if (exp.isEmpty()) return 0;
		AgentRewardsEntity wallet = lockedWallet(participantCode);
		long defeatsThisRoom = ledger.countByParticipantCodeAndMissionIdAndRoomGenerationAndKind(
				participantCode, missionId, roomGeneration, RewardLedgerEntity.ENEMY_EXP);
		if (defeatsThisRoom >= RewardCatalog.MAX_REWARDED_DEFEATS_PER_ROOM) return 0;
		int granted = capToMission(participantCode, missionId, rankCode, exp.get());
		if (granted <= 0) return 0;
		wallet.addExp(granted);
		wallets.save(wallet);
		ledger.save(RewardLedgerEntity.of(participantCode, RewardLedgerEntity.ENEMY_EXP, RewardLedgerEntity.EXP,
				granted, missionId, roomGeneration, enemyType.trim().toUpperCase(java.util.Locale.ROOT), null));
		return granted;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void bossDefeated(String participantCode, String missionId, String rankCode, UUID roomGeneration) {
		String key = "BOSS_EXP:" + participantCode + ":" + missionId;
		AgentRewardsEntity wallet = lockedWallet(participantCode);
		if (ledger.existsByDedupeKey(key)) return;
		int granted = capToMission(participantCode, missionId, rankCode, RewardCatalog.bossExp(rankCode));
		if (granted <= 0) return;
		wallet.addExp(granted);
		wallets.save(wallet);
		ledger.save(RewardLedgerEntity.of(participantCode, RewardLedgerEntity.BOSS_EXP, RewardLedgerEntity.EXP,
				granted, missionId, roomGeneration, "BOSS", key));
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void firstClear(String participantCode, String missionId, String rankCode) {
		String key = "FIRST_CLEAR:" + participantCode + ":" + missionId;
		AgentRewardsEntity wallet = lockedWallet(participantCode);
		if (ledger.existsByDedupeKey(key)) return;
		int coins = RewardCatalog.firstClearCoins(rankCode);
		wallet.addCoins(coins);
		wallets.save(wallet);
		ledger.save(RewardLedgerEntity.of(participantCode, RewardLedgerEntity.FIRST_CLEAR_COINS, RewardLedgerEntity.COINS,
				coins, missionId, null, rankCode, key));
	}

	/** Spends EXP on the next level of one attribute of one owned gun (each gun has its own prices). */
	@Transactional
	public RewardsDto upgrade(String participantCode, String weaponId, String rawStat) {
		GunStat stat = GunStat.parse(rawStat)
				.orElseThrow(() -> new IllegalArgumentException("Unknown gun upgrade"));
		RewardCatalog.Weapon weapon = RewardCatalog.weapon(weaponId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown weapon"));
		AgentRewardsEntity wallet = lockedWallet(participantCode);
		if (!wallet.owns(weapon.id())) {
			throw new IllegalStateException("Buy this weapon before upgrading it");
		}
		int level = wallet.level(weapon.id(), stat);
		if (level >= RewardCatalog.maxUpgradeLevel()) {
			throw new IllegalStateException("This upgrade is already at its maximum level");
		}
		int cost = weapon.upgradeCosts().get(level);
		if (wallet.getExp() < cost) {
			throw new IllegalStateException("Not enough EXP for this upgrade");
		}
		wallet.addExp(-cost);
		wallet.setLevel(weapon.id(), stat, level + 1);
		wallets.save(wallet);
		ledger.save(RewardLedgerEntity.of(participantCode, RewardLedgerEntity.GUN_UPGRADE, RewardLedgerEntity.EXP,
				-cost, null, null, weapon.id() + ":" + stat.name() + ":" + (level + 1), null));
		return toDto(wallet);
	}

	/** Spends coins on a new gun and equips it. */
	@Transactional
	public RewardsDto purchase(String participantCode, String weaponId) {
		RewardCatalog.Weapon weapon = RewardCatalog.weapon(weaponId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown weapon"));
		AgentRewardsEntity wallet = lockedWallet(participantCode);
		if (wallet.owns(weapon.id())) {
			throw new IllegalStateException("You already own this weapon");
		}
		if (wallet.getCoins() < weapon.price()) {
			throw new IllegalStateException("Not enough coins for this weapon");
		}
		wallet.addCoins(-weapon.price());
		wallet.addWeapon(weapon.id());
		wallet.equip(weapon.id());
		wallets.save(wallet);
		ledger.save(RewardLedgerEntity.of(participantCode, RewardLedgerEntity.GUN_PURCHASE, RewardLedgerEntity.COINS,
				-weapon.price(), null, null, weapon.id(), null));
		return toDto(wallet);
	}

	@Transactional
	public RewardsDto equip(String participantCode, String weaponId) {
		AgentRewardsEntity wallet = lockedWallet(participantCode);
		if (weaponId == null || !wallet.owns(weaponId)) {
			throw new IllegalStateException("You do not own this weapon");
		}
		wallet.equip(weaponId);
		wallets.save(wallet);
		return toDto(wallet);
	}

	/** The free starter character, chosen once when the agent first signs in. */
	@Transactional
	public RewardsDto chooseStarterCharacter(String participantCode, String characterId) {
		RewardCatalog.Character character = RewardCatalog.character(characterId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown character"));
		AgentRewardsEntity wallet = lockedWallet(participantCode);
		if (!wallet.ownedCharacters().isEmpty()) {
			throw new IllegalStateException("Your free starting agent has already been chosen");
		}
		wallet.addCharacter(character.id());
		wallets.save(wallet);
		ledger.save(RewardLedgerEntity.of(participantCode, RewardLedgerEntity.STARTER_CHARACTER, RewardLedgerEntity.COINS,
				0, null, null, character.id(), "STARTER:" + participantCode));
		return toDto(wallet);
	}

	/** Buys another starter character with coins (after the free pick). */
	@Transactional
	public RewardsDto purchaseCharacter(String participantCode, String characterId) {
		RewardCatalog.Character character = RewardCatalog.character(characterId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown character"));
		AgentRewardsEntity wallet = lockedWallet(participantCode);
		if (wallet.ownedCharacters().isEmpty()) {
			throw new IllegalStateException("Choose your free starting agent first");
		}
		if (wallet.ownsCharacter(character.id())) {
			throw new IllegalStateException("You already own this agent");
		}
		if (wallet.getCoins() < character.price()) {
			throw new IllegalStateException("Not enough coins for this agent");
		}
		wallet.addCoins(-character.price());
		wallet.addCharacter(character.id());
		wallets.save(wallet);
		ledger.save(RewardLedgerEntity.of(participantCode, RewardLedgerEntity.CHARACTER_PURCHASE, RewardLedgerEntity.COINS,
				-character.price(), null, null, character.id(), null));
		return toDto(wallet);
	}

	/** Remaining room in the per-mission EXP cap, applied to a grant. */
	private int capToMission(String participantCode, String missionId, String rankCode, int amount) {
		long earned = ledger.sumForMission(participantCode, missionId, EXP_GRANTS);
		long room = RewardCatalog.missionExpCap(rankCode) - earned;
		return (int) Math.max(0, Math.min(amount, room));
	}

	private AgentRewardsEntity lockedWallet(String participantCode) {
		return wallets.findForUpdate(participantCode).orElseGet(() -> {
			wallets.saveAndFlush(AgentRewardsEntity.fresh(participantCode));
			return wallets.findForUpdate(participantCode).orElseThrow();
		});
	}

	private RewardsDto toDto(AgentRewardsEntity wallet) {
		List<RewardsDto.WeaponOfferDto> weapons = RewardCatalog.WEAPONS.stream()
				.map(w -> {
					Map<String, Integer> upgrades = new LinkedHashMap<>();
					for (GunStat stat : GunStat.values()) upgrades.put(stat.name(), wallet.level(w.id(), stat));
					return new RewardsDto.WeaponOfferDto(
							w.id(), w.displayName(), w.price(), wallet.owns(w.id()), upgrades, w.upgradeCosts());
				})
				.toList();
		List<RewardsDto.CharacterOfferDto> characters = RewardCatalog.CHARACTERS.stream()
				.map(c -> new RewardsDto.CharacterOfferDto(c.id(), c.displayName(), c.price(), wallet.ownsCharacter(c.id())))
				.toList();
		return new RewardsDto(
				wallet.getExp(),
				wallet.getCoins(),
				wallet.getExpEarned(),
				wallet.getCoinsEarned(),
				RewardCatalog.maxUpgradeLevel(),
				wallet.getEquippedWeapon(),
				weapons,
				!wallet.ownedCharacters().isEmpty(),
				characters);
	}
}
