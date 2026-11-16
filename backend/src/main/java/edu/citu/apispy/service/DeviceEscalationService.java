package edu.citu.apispy.service;

import edu.citu.apispy.content.ContentLoader;
import edu.citu.apispy.content.EscalationFile;
import edu.citu.apispy.content.MissionEscalationDefinition;
import edu.citu.apispy.domain.simulation.ApiContract;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implements Proposal/SRS 1 → 3 → 5 mandatory API-device escalation.
 * Final lock appears only on API-driven retries and is not included in the 1/3/5 count,
 * except for boss finales that opt into full multi-device coverage from the first attempt.
 * Adaptive practice doors are never part of this pool.
 */
@Service
public class DeviceEscalationService {

	public static final int TIER_INITIAL = 1;
	public static final int TIER_AFTER_FIRST_API_RESET = 3;
	public static final int TIER_AFTER_SECOND_API_RESET = 5;
	public static final int MAX_API_RESETS_FOR_TIER = 2;

	/** Module 1 Recruit Level 7 boss — always full concept coverage + final lock. */
	public static final String MODULE1_RECRUIT_BOSS_MISSION_ID = "m1-recruit-l7";

	private final Map<String, MissionEscalationDefinition> byMissionId;
	private final ContractCatalogService contractCatalogService;

	public DeviceEscalationService(ContentLoader contentLoader, ContractCatalogService contractCatalogService) {
		EscalationFile file = contentLoader.load("content/escalation/module1-escalation.json", EscalationFile.class);
		Map<String, MissionEscalationDefinition> map = new LinkedHashMap<>();
		for (MissionEscalationDefinition definition : file.missions()) {
			map.put(definition.missionId(), definition);
		}
		this.byMissionId = Map.copyOf(map);
		this.contractCatalogService = contractCatalogService;
	}

	public Optional<MissionEscalationDefinition> find(String missionId) {
		return Optional.ofNullable(byMissionId.get(missionId));
	}

	/** Global 1 → 3 → 5 curve (final lock excluded). Unchanged for non-boss missions. */
	public int mandatoryDeviceCount(int apiResetCount) {
		int capped = Math.max(0, Math.min(apiResetCount, MAX_API_RESETS_FOR_TIER));
		return switch (capped) {
			case 0 -> TIER_INITIAL;
			case 1 -> TIER_AFTER_FIRST_API_RESET;
			default -> TIER_AFTER_SECOND_API_RESET;
		};
	}

	/**
	 * Mission-aware mandatory count. Boss finales require full pool coverage on every attempt
	 * without altering the global curve used by Levels 1–6.
	 */
	public int mandatoryDeviceCount(String missionId, int apiResetCount) {
		if (isBossFinale(missionId)) {
			return TIER_AFTER_SECOND_API_RESET;
		}
		return mandatoryDeviceCount(apiResetCount);
	}

	public boolean finalLockRequired(int apiResetCount) {
		return apiResetCount >= 1;
	}

	/** Mission-aware final lock. Boss finales require the vault seal on the first attempt. */
	public boolean finalLockRequired(String missionId, int apiResetCount) {
		if (isBossFinale(missionId)) {
			return true;
		}
		return finalLockRequired(apiResetCount);
	}

	public boolean isBossFinale(String missionId) {
		return MODULE1_RECRUIT_BOSS_MISSION_ID.equals(missionId);
	}

	public List<ApiContract> requiredMandatoryContracts(String missionId, int apiResetCount) {
		return requiredMandatoryContracts(missionId, apiResetCount, null);
	}

	/**
	 * When {@code selectedChallengeIds} is present (active room selection), those authored
	 * contracts are authoritative for the run. Otherwise the fixed escalation prefix is used.
	 */
	public List<ApiContract> requiredMandatoryContracts(
			String missionId,
			int apiResetCount,
			List<String> selectedChallengeIds) {
		if (selectedChallengeIds != null && !selectedChallengeIds.isEmpty()) {
			List<ApiContract> selected = new ArrayList<>();
			for (String challengeId : selectedChallengeIds) {
				selected.add(contractCatalogService.requireByChallengeId(challengeId));
			}
			return List.copyOf(selected);
		}
		MissionEscalationDefinition definition = byMissionId.get(missionId);
		if (definition == null) {
			ApiContract primary = fallbackPrimary(missionId);
			return primary == null ? List.of() : List.of(primary);
		}
		int count = mandatoryDeviceCount(missionId, apiResetCount);
		List<String> ids = definition.mandatoryChallengeIds();
		List<ApiContract> required = new ArrayList<>();
		for (int i = 0; i < Math.min(count, ids.size()); i++) {
			required.add(contractCatalogService.requireByChallengeId(ids.get(i)));
		}
		if (finalLockRequired(missionId, apiResetCount) && definition.finalLockChallengeId() != null) {
			required.add(contractCatalogService.requireByChallengeId(definition.finalLockChallengeId()));
		}
		return List.copyOf(required);
	}

	public List<String> requiredDeviceIds(String missionId, int apiResetCount) {
		return requiredDeviceIds(missionId, apiResetCount, null);
	}

	public List<String> requiredDeviceIds(String missionId, int apiResetCount, List<String> selectedChallengeIds) {
		return requiredMandatoryContracts(missionId, apiResetCount, selectedChallengeIds).stream()
				.map(ApiContract::getDeviceId)
				.toList();
	}

	public boolean isMandatoryDevice(
			String missionId,
			String deviceId,
			int apiResetCount,
			List<String> selectedChallengeIds) {
		return requiredDeviceIds(missionId, apiResetCount, selectedChallengeIds).contains(deviceId);
	}

	public List<ApiContract> verificationContracts(String missionId) {
		MissionEscalationDefinition definition = byMissionId.get(missionId);
		if (definition == null || definition.verificationChallengeIds() == null) {
			return contractCatalogService.findByMissionId(missionId).stream()
					.filter(c -> !c.getDeviceId().startsWith("final-lock-"))
					.filter(c -> !isEscalationOnlyDevice(missionId, c.getChallengeId()))
					.toList();
		}
		List<ApiContract> contracts = new ArrayList<>();
		for (String challengeId : definition.verificationChallengeIds()) {
			contracts.add(contractCatalogService.requireByChallengeId(challengeId));
		}
		return List.copyOf(contracts);
	}

	public boolean isMandatoryDevice(String missionId, String deviceId, int apiResetCount) {
		return requiredDeviceIds(missionId, apiResetCount).contains(deviceId);
	}

	public boolean isPracticeDevice(String deviceId) {
		return deviceId != null && deviceId.startsWith("practice-");
	}

	private boolean isEscalationOnlyDevice(String missionId, String challengeId) {
		MissionEscalationDefinition definition = byMissionId.get(missionId);
		if (definition == null) {
			return false;
		}
		List<String> verification = definition.verificationChallengeIds() == null
				? List.of()
				: definition.verificationChallengeIds();
		if (verification.contains(challengeId)) {
			return false;
		}
		if (challengeId.equals(definition.finalLockChallengeId())) {
			return true;
		}
		return definition.mandatoryChallengeIds() != null
				&& definition.mandatoryChallengeIds().contains(challengeId)
				&& !verification.contains(challengeId);
	}

	private ApiContract fallbackPrimary(String missionId) {
		return contractCatalogService.findByMissionId(missionId).stream().findFirst().orElse(null);
	}
}
