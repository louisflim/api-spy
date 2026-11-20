package edu.citu.apispy.dto;

import java.util.List;
import java.util.Map;

/**
 * The agent's gameplay rewards: EXP and coin balances, gun upgrade levels and the gun catalog.
 * Gameplay only — never an academic score.
 */
public record RewardsDto(
		int exp,
		int coins,
		int expEarned,
		int coinsEarned,
		int maxUpgradeLevel,
		String equippedWeapon,
		List<WeaponOfferDto> weapons,
		/** False until the agent makes the free starter-character pick. */
		boolean starterCharacterChosen,
		List<CharacterOfferDto> characters
) {
	/**
	 * A gun with this agent's upgrade levels for it (DAMAGE / FIRE_RATE / VELOCITY) and the EXP
	 * price of each next level (index = current level).
	 */
	public record WeaponOfferDto(
			String id, String displayName, int price, boolean owned,
			Map<String, Integer> upgrades, List<Integer> upgradeCosts) {
	}

	public record CharacterOfferDto(String id, String displayName, int price, boolean owned) {
	}
}
