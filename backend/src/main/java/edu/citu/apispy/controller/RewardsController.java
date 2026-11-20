package edu.citu.apispy.controller;

import edu.citu.apispy.dto.RewardActionDto;
import edu.citu.apispy.dto.RewardsDto;
import edu.citu.apispy.security.AuthenticatedPlayer;
import edu.citu.apispy.service.rewards.RewardService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in agent's EXP, coins, gun upgrades and gun shop (gameplay only). */
@RestController
@Validated
@RequestMapping("/api/rewards")
public class RewardsController {

	private final RewardService rewards;

	public RewardsController(RewardService rewards) {
		this.rewards = rewards;
	}

	@GetMapping
	public RewardsDto mine() {
		return rewards.get(AuthenticatedPlayer.requireUserIdString());
	}

	@PostMapping("/upgrade")
	public RewardsDto upgrade(@Valid @RequestBody RewardActionDto request) {
		return rewards.upgrade(AuthenticatedPlayer.requireUserIdString(), request.weaponId(), request.stat());
	}

	@PostMapping("/purchase")
	public RewardsDto purchase(@Valid @RequestBody RewardActionDto request) {
		return rewards.purchase(AuthenticatedPlayer.requireUserIdString(), request.weaponId());
	}

	@PostMapping("/characters/starter")
	public RewardsDto chooseStarter(@Valid @RequestBody RewardActionDto request) {
		return rewards.chooseStarterCharacter(AuthenticatedPlayer.requireUserIdString(), request.characterId());
	}

	@PostMapping("/characters/purchase")
	public RewardsDto purchaseCharacter(@Valid @RequestBody RewardActionDto request) {
		return rewards.purchaseCharacter(AuthenticatedPlayer.requireUserIdString(), request.characterId());
	}

	@PostMapping("/equip")
	public RewardsDto equip(@Valid @RequestBody RewardActionDto request) {
		return rewards.equip(AuthenticatedPlayer.requireUserIdString(), request.weaponId());
	}
}
