package edu.citu.apispy.service;

import edu.citu.apispy.content.MissionEscalationDefinition;
import edu.citu.apispy.domain.simulation.ApiContract;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic authored-scenario selection bound to room generation.
 * Does not invent contracts — only picks from predefined pools.
 */
@Service
public class ScenarioSelectionService {

	private final DeviceEscalationService deviceEscalationService;
	private final ContractCatalogService contractCatalogService;

	public ScenarioSelectionService(
			DeviceEscalationService deviceEscalationService,
			ContractCatalogService contractCatalogService) {
		this.deviceEscalationService = deviceEscalationService;
		this.contractCatalogService = contractCatalogService;
	}

	/**
	 * Selects mandatory challenge IDs for the active tier.
	 * First career/room selection uses the fixed escalation prefix (stable curriculum).
	 * Later eligible generations (API reset / replay) rotate through authored pools and
	 * avoid immediately repeating the previous run when alternatives exist.
	 */
	public List<String> selectMandatoryChallengeIds(
			String missionId,
			int apiResetCount,
			UUID roomGeneration,
			List<String> previousChallengeIds) {
		MissionEscalationDefinition definition = deviceEscalationService.find(missionId).orElse(null);
		boolean rotate = previousChallengeIds != null && !previousChallengeIds.isEmpty();
		if (!rotate) {
			return deviceEscalationService.requiredMandatoryContracts(missionId, apiResetCount).stream()
					.map(ApiContract::getChallengeId)
					.toList();
		}

		int count = deviceEscalationService.mandatoryDeviceCount(apiResetCount);
		List<String> selected = new ArrayList<>();
		Set<String> usedThisRun = new LinkedHashSet<>();
		Set<String> previous = new LinkedHashSet<>(previousChallengeIds);

		for (int slot = 0; slot < count; slot++) {
			List<String> pool = poolForSlot(definition, slot);
			String pick = pickFromPool(pool, roomGeneration, missionId, slot, previous, usedThisRun);
			selected.add(pick);
			usedThisRun.add(pick);
		}

		if (deviceEscalationService.finalLockRequired(apiResetCount) && definition != null
				&& definition.finalLockChallengeId() != null) {
			selected.add(definition.finalLockChallengeId());
		}
		return List.copyOf(selected);
	}

	/** Verification plan for TVP — prefer pool when authored. */
	public List<ApiContract> verificationPlan(String missionId, List<String> selectedMandatoryIds) {
		MissionEscalationDefinition definition = deviceEscalationService.find(missionId).orElse(null);
		List<String> ids = new ArrayList<>();
		if (selectedMandatoryIds != null && !selectedMandatoryIds.isEmpty()) {
			String primary = selectedMandatoryIds.getFirst();
			if (!primary.startsWith("chal-m1-final-lock-")) {
				ids.add(primary);
			}
		}
		List<String> verifyPool = definition == null || definition.verificationScenarioPool() == null
				|| definition.verificationScenarioPool().isEmpty()
				? (definition == null ? List.of() : definition.verificationChallengeIds())
				: definition.verificationScenarioPool();
		for (String candidate : verifyPool) {
			if (!ids.contains(candidate)) {
				ids.add(candidate);
				break;
			}
		}
		if (ids.size() < 2 && definition != null) {
			for (String fallback : definition.verificationChallengeIds()) {
				if (!ids.contains(fallback)) {
					ids.add(fallback);
				}
				if (ids.size() >= 2) {
					break;
				}
			}
		}
		List<ApiContract> contracts = new ArrayList<>();
		for (String id : ids) {
			contracts.add(contractCatalogService.requireByChallengeId(id));
		}
		return List.copyOf(contracts);
	}

	private List<String> poolForSlot(MissionEscalationDefinition definition, int slot) {
		if (definition == null) {
			return List.of();
		}
		List<List<String>> pools = definition.mandatoryScenarioPools();
		if (pools != null && slot < pools.size() && pools.get(slot) != null && !pools.get(slot).isEmpty()) {
			return pools.get(slot);
		}
		List<String> fallback = definition.mandatoryChallengeIds();
		if (slot < fallback.size()) {
			return List.of(fallback.get(slot));
		}
		return List.of();
	}

	private String pickFromPool(
			List<String> pool,
			UUID roomGeneration,
			String missionId,
			int slot,
			Set<String> previous,
			Set<String> usedThisRun) {
		if (pool.isEmpty()) {
			throw new IllegalStateException("Empty scenario pool for mission " + missionId + " slot " + slot);
		}
		List<String> candidates = pool.stream()
				.filter(id -> !usedThisRun.contains(id))
				.filter(id -> contractCatalogService.findByChallengeId(id).isPresent())
				.toList();
		if (candidates.isEmpty()) {
			candidates = pool.stream()
					.filter(id -> contractCatalogService.findByChallengeId(id).isPresent())
					.toList();
		}
		List<String> avoidRepeat = candidates.stream().filter(id -> !previous.contains(id)).toList();
		if (!avoidRepeat.isEmpty()) {
			candidates = avoidRepeat;
		}
		int index = Math.floorMod(stableHash(roomGeneration, missionId, slot), candidates.size());
		return candidates.get(index);
	}

	static int stableHash(UUID roomGeneration, String missionId, int slot) {
		String material = roomGeneration + "|" + missionId + "|" + slot;
		byte[] bytes = material.getBytes(StandardCharsets.UTF_8);
		int hash = 0;
		for (byte value : bytes) {
			hash = 31 * hash + (value & 0xff);
		}
		return hash;
	}
}
