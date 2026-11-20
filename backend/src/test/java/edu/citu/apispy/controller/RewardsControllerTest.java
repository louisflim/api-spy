package edu.citu.apispy.controller;

import com.jayway.jsonpath.JsonPath;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.MissionProgressDto;
import edu.citu.apispy.dto.SimulateRequestDto;
import edu.citu.apispy.service.ContractCatalogService;
import edu.citu.apispy.service.MissionProgressService;
import edu.citu.apispy.service.RequestSimulationService;
import edu.citu.apispy.service.rewards.RewardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proposal rewards: enemy defeats give EXP that upgrades gun attributes; first-clear coins
 * (10 / 20 / 30 by rank) buy new guns; anti-farming caps per room run and per mission.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RewardsControllerTest {

	private static final String PASSWORD = "Password1!";
	private static final String MISSION = "m1-recruit-l1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RewardService rewards;

	@Autowired
	private MissionProgressService progress;

	@Autowired
	private RequestSimulationService simulate;

	@Autowired
	private ContractCatalogService contracts;

	@Test
	void clearingAMissionPaysFirstClearCoinsOnceAndReplaysPayNothing() {
		String who = "flow-" + UUID.randomUUID();
		clearActiveRoom(who);
		assertEquals(10, rewards.get(who).coins(), "Recruit first clear");

		progress.startReplay(who, MISSION);
		clearActiveRoom(who);
		assertEquals(10, rewards.get(who).coins(), "replay grants no duplicate first-clear coins");
	}

	private void clearActiveRoom(String who) {
		MissionProgressDto room = progress.getOrCreate(who, MISSION);
		for (MissionProgressDto.MandatoryDeviceDto device : room.mandatoryDevices()) {
			ApiContract c = contracts.requireByChallengeId(device.challengeId());
			String path = c.getPathTemplate();
			for (Map.Entry<String, String> p : c.getPathParameters().entrySet()) {
				path = path.replace("{" + p.getKey() + "}", p.getValue());
			}
			simulate.simulate(who, new SimulateRequestDto(who, MISSION, c.getDeviceId(), c.getChallengeId(),
					c.getExpectedMethod(), path, Map.of(), "", "MISSION"));
		}
		progress.completeAfterExit(who, MISSION, progress.getOrCreate(who, MISSION).roomGeneration());
	}

	@Test
	void signedOutVisitorsCannotReadRewards() throws Exception {
		mockMvc.perform(get("/api/rewards")).andExpect(status().isUnauthorized());
	}

	@Test
	void newAgentStartsWithTheSidearmAndEmptyBalances() throws Exception {
		MockHttpSession agent = sessionFor("rewardsnew", "rewardsnew@example.test");
		mockMvc.perform(get("/api/rewards").session(agent))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.exp").value(0))
				.andExpect(jsonPath("$.coins").value(0))
				.andExpect(jsonPath("$.equippedWeapon").value("starter_sidearm"))
				.andExpect(jsonPath("$.weapons.length()").value(3))
				.andExpect(jsonPath("$.weapons[0].owned").value(true))
				.andExpect(jsonPath("$.weapons[1].owned").value(false))
				.andExpect(jsonPath("$.weapons[0].upgrades.DAMAGE").value(0))
				.andExpect(jsonPath("$.maxUpgradeLevel").value(3));
	}

	@Test
	void enemyDefeatsGrantExpOnlyForTheActiveRoomAndStopAtTheRoomCap() throws Exception {
		MockHttpSession agent = sessionFor("rewardskills", "rewardskills@example.test");
		String generation = activeGeneration(agent);

		defeat(agent, generation, "PATROL_GUARD").andExpect(jsonPath("$.expAwarded").value(5));
		defeat(agent, generation, "SHOCK_ENFORCER").andExpect(jsonPath("$.expAwarded").value(8));
		defeat(agent, generation, "NOT_AN_ENEMY").andExpect(jsonPath("$.expAwarded").value(0));
		defeat(agent, UUID.randomUUID().toString(), "PATROL_GUARD").andExpect(jsonPath("$.expAwarded").value(0));

		// 2 rewarded so far; only 10 more defeats in this room run earn EXP (cap 12).
		int total = 13;
		for (int i = 0; i < 15; i++) {
			String body = defeat(agent, generation, "PATROL_GUARD").andReturn().getResponse().getContentAsString();
			total += (int) JsonPath.read(body, "$.expAwarded");
		}
		assertEquals(13 + 10 * 5, total);
		mockMvc.perform(get("/api/rewards").session(agent)).andExpect(jsonPath("$.exp").value(total));
	}

	@Test
	void missionExpIsCappedAcrossRoomRuns() {
		String who = "cap-" + UUID.randomUUID();
		int total = 0;
		for (int run = 0; run < 10; run++) {
			UUID generation = UUID.randomUUID();
			for (int i = 0; i < 12; i++) {
				total += rewards.enemyDefeated(who, MISSION, "RECRUIT", generation, "SHOCK_ENFORCER");
			}
		}
		assertEquals(150, total, "Recruit mission EXP cap");
		assertEquals(150, rewards.get(who).exp());
		assertEquals(0, rewards.enemyDefeated(who, "m1-recruit-l2", "RECRUIT", UUID.randomUUID(), "UNKNOWN"));
		assertEquals(8, rewards.enemyDefeated(who, "m1-field-l2", "FIELD_AGENT", UUID.randomUUID(), "PATROL_GUARD"),
				"Field Agent enemies are worth 1.5x");
	}

	@Test
	void firstClearCoinsAndBossExpAreGrantedOncePerMission() {
		String who = "clear-" + UUID.randomUUID();
		rewards.firstClear(who, "m1-recruit-l1", "RECRUIT");
		rewards.firstClear(who, "m1-recruit-l1", "RECRUIT");
		rewards.firstClear(who, "m1-field-l1", "FIELD_AGENT");
		rewards.firstClear(who, "m1-special-l1", "SPECIAL_AGENT");
		assertEquals(10 + 20 + 30, rewards.get(who).coins());

		rewards.bossDefeated(who, "m1-recruit-l7", "RECRUIT", UUID.randomUUID());
		rewards.bossDefeated(who, "m1-recruit-l7", "RECRUIT", UUID.randomUUID());
		assertEquals(40, rewards.get(who).exp());
	}

	@Test
	void eachGunHasItsOwnUpgradeLevelsAndPrices() throws Exception {
		MockHttpSession agent = sessionFor("rewardsupgrade", "rewardsupgrade@example.test");
		String who = playerId(agent);
		upgrade(agent, "starter_sidearm", "DAMAGE").andExpect(status().isBadRequest());

		for (int m = 1; m <= 3; m++) {
			UUID generation = UUID.randomUUID();
			for (int i = 0; i < 12; i++) rewards.enemyDefeated(who, "m1-special-l" + m, "SPECIAL_AGENT", generation, "SHOCK_ENFORCER");
		}
		int exp = rewards.get(who).exp();
		assertEquals(3 * 12 * 16, exp);

		// Field Pistol prices: 30 / 70 / 120.
		upgrade(agent, "starter_sidearm", "DAMAGE").andExpect(status().isOk())
				.andExpect(jsonPath("$.weapons[0].upgrades.DAMAGE").value(1))
				.andExpect(jsonPath("$.weapons[0].upgradeCosts[0]").value(30))
				.andExpect(jsonPath("$.exp").value(exp - 30));
		upgrade(agent, "starter_sidearm", "damage").andExpect(jsonPath("$.weapons[0].upgrades.DAMAGE").value(2));
		upgrade(agent, "starter_sidearm", "DAMAGE").andExpect(jsonPath("$.weapons[0].upgrades.DAMAGE").value(3))
				.andExpect(jsonPath("$.exp").value(exp - 30 - 70 - 120));
		upgrade(agent, "starter_sidearm", "DAMAGE").andExpect(status().isBadRequest());
		upgrade(agent, "starter_sidearm", "LASER").andExpect(status().isBadRequest());

		// Other guns keep their own levels; a gun must be owned before it can be upgraded.
		upgrade(agent, "assault_rifle", "DAMAGE").andExpect(status().isBadRequest());
		for (int level = 1; level <= 6; level++) rewards.firstClear(who, "m1-recruit-l" + level, "RECRUIT");
		mockMvc.perform(post("/api/rewards/purchase").with(csrf()).session(agent)
						.contentType(MediaType.APPLICATION_JSON).content("{\"weaponId\":\"assault_rifle\"}"))
				.andExpect(status().isOk());
		int before = exp - 30 - 70 - 120;
		// AK-47 prices: 50 / 110 / 180 — and the pistol's levels are unchanged.
		upgrade(agent, "assault_rifle", "FIRE_RATE").andExpect(status().isOk())
				.andExpect(jsonPath("$.weapons[1].upgrades.FIRE_RATE").value(1))
				.andExpect(jsonPath("$.weapons[1].upgrades.DAMAGE").value(0))
				.andExpect(jsonPath("$.weapons[0].upgrades.DAMAGE").value(3))
				.andExpect(jsonPath("$.weapons[0].upgrades.FIRE_RATE").value(0))
				.andExpect(jsonPath("$.exp").value(before - 50));
		upgrade(agent, "ray_gun", "DAMAGE").andExpect(status().isBadRequest());
	}

	@Test
	void coinsBuyAndEquipGuns() throws Exception {
		MockHttpSession agent = sessionFor("rewardsshop", "rewardsshop@example.test");
		String who = playerId(agent);
		action(agent, "purchase", "assault_rifle").andExpect(status().isBadRequest());
		action(agent, "equip", "assault_rifle").andExpect(status().isBadRequest());

		for (int level = 1; level <= 6; level++) rewards.firstClear(who, "m1-recruit-l" + level, "RECRUIT");
		action(agent, "purchase", "assault_rifle").andExpect(status().isOk())
				.andExpect(jsonPath("$.coins").value(0))
				.andExpect(jsonPath("$.equippedWeapon").value("assault_rifle"))
				.andExpect(jsonPath("$.weapons[1].owned").value(true));
		action(agent, "purchase", "assault_rifle").andExpect(status().isBadRequest());
		action(agent, "equip", "starter_sidearm").andExpect(status().isOk())
				.andExpect(jsonPath("$.equippedWeapon").value("starter_sidearm"));
		action(agent, "purchase", "ray_gun").andExpect(status().isBadRequest());
	}

	@Test
	void newAgentPicksOneFreeCharacterAndBuysTheOthersWithCoins() throws Exception {
		MockHttpSession agent = sessionFor("rewardschar", "rewardschar@example.test");
		String who = playerId(agent);
		mockMvc.perform(get("/api/rewards").session(agent))
				.andExpect(jsonPath("$.starterCharacterChosen").value(false))
				.andExpect(jsonPath("$.characters.length()").value(3))
				.andExpect(jsonPath("$.characters[0].owned").value(false));

		// Buying before the free pick is refused.
		character(agent, "purchase", "agent-boy").andExpect(status().isBadRequest());

		character(agent, "starter", "agent-girl").andExpect(status().isOk())
				.andExpect(jsonPath("$.starterCharacterChosen").value(true))
				.andExpect(jsonPath("$.characters[2].owned").value(true))
				.andExpect(jsonPath("$.coins").value(0));
		// Only one free pick.
		character(agent, "starter", "rookie").andExpect(status().isBadRequest());
		character(agent, "starter", "nobody").andExpect(status().isBadRequest());

		// The others cost 50 coins each.
		character(agent, "purchase", "rookie").andExpect(status().isBadRequest());
		for (int level = 1; level <= 5; level++) rewards.firstClear(who, "m1-recruit-l" + level, "RECRUIT");
		character(agent, "purchase", "rookie").andExpect(status().isOk())
				.andExpect(jsonPath("$.coins").value(0))
				.andExpect(jsonPath("$.characters[0].owned").value(true));
		character(agent, "purchase", "agent-girl").andExpect(status().isBadRequest());
	}

	private org.springframework.test.web.servlet.ResultActions character(MockHttpSession agent, String path, String id)
			throws Exception {
		return mockMvc.perform(post("/api/rewards/characters/" + path)
				.with(csrf())
				.session(agent)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"characterId\":\"" + id + "\"}"));
	}

	private org.springframework.test.web.servlet.ResultActions defeat(MockHttpSession agent, String generation, String type)
			throws Exception {
		return mockMvc.perform(post("/api/missions/" + MISSION + "/enemy-defeated")
						.with(csrf())
						.session(agent)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"roomGeneration\":\"" + generation + "\",\"enemyType\":\"" + type + "\"}"))
				.andExpect(status().isOk());
	}

	private org.springframework.test.web.servlet.ResultActions upgrade(MockHttpSession agent, String weaponId, String stat)
			throws Exception {
		return mockMvc.perform(post("/api/rewards/upgrade")
				.with(csrf())
				.session(agent)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"weaponId\":\"" + weaponId + "\",\"stat\":\"" + stat + "\"}"));
	}

	private org.springframework.test.web.servlet.ResultActions action(MockHttpSession agent, String path, String weaponId)
			throws Exception {
		return mockMvc.perform(post("/api/rewards/" + path)
				.with(csrf())
				.session(agent)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"weaponId\":\"" + weaponId + "\"}"));
	}

	private String activeGeneration(MockHttpSession agent) throws Exception {
		String progress = mockMvc.perform(get("/api/missions/" + MISSION + "/progress").session(agent))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(progress, "$.roomGeneration");
	}

	private String playerId(MockHttpSession session) throws Exception {
		String me = mockMvc.perform(get("/api/me").session(session)).andReturn().getResponse().getContentAsString();
		return JsonPath.read(me, "$.userId");
	}

	private MockHttpSession sessionFor(String username, String email) throws Exception {
		mockMvc.perform(post("/api/auth/register")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\""
						+ PASSWORD + "\",\"confirmPassword\":\"" + PASSWORD + "\"}"));
		return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getRequest().getSession();
	}
}
