package edu.citu.apispy.service;

import edu.citu.apispy.content.ChallengeDefinition;
import edu.citu.apispy.content.ChallengeFile;
import edu.citu.apispy.content.ContentLoader;
import edu.citu.apispy.domain.simulation.ApiContract;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Authored challenge contracts loaded from JSON. No unlimited random generation.
 * Includes graded Module 1 contracts plus Module 0 training / practice contracts.
 */
@Service
public class ContractCatalogService {

	public static final String DAY1_DOOR_CONTRACT_ID = "chal-m1-get-door-v1";
	public static final String DAY1_DOOR_DEVICE_ID = "door-alpha";
	public static final String MODULE0_TRAINING_CHALLENGE_ID = "chal-m0-training-terminal";

	private final Map<String, ApiContract> contractsByChallengeId;
	private final Map<String, ApiContract> primaryByDeviceId;
	private final Map<String, List<ApiContract>> byMissionId;

	public ContractCatalogService(ContentLoader contentLoader) {
		Map<String, ApiContract> byChallenge = new LinkedHashMap<>();
		Map<String, ApiContract> byDevice = new LinkedHashMap<>();
		Map<String, List<ApiContract>> byMission = new LinkedHashMap<>();

		loadFile(contentLoader, "content/challenges/module1-challenges.json", byChallenge, byDevice, byMission);
		loadFile(contentLoader, "content/challenges/module1-scenario-bank-extra.json", byChallenge, byDevice, byMission);
		loadFile(contentLoader, "content/challenges/module1-boss-l7-challenges.json", byChallenge, byDevice, byMission);
		loadFile(contentLoader, "content/challenges/module0-training-challenges.json", byChallenge, byDevice, byMission);

		this.contractsByChallengeId = Map.copyOf(byChallenge);
		this.primaryByDeviceId = Map.copyOf(byDevice);
		Map<String, List<ApiContract>> immutableMissions = new LinkedHashMap<>();
		byMission.forEach((key, value) -> immutableMissions.put(key, List.copyOf(value)));
		this.byMissionId = Map.copyOf(immutableMissions);
	}

	private static void loadFile(
			ContentLoader contentLoader,
			String location,
			Map<String, ApiContract> byChallenge,
			Map<String, ApiContract> byDevice,
			Map<String, List<ApiContract>> byMission) {
		ChallengeFile file = contentLoader.load(location, ChallengeFile.class);
		for (ChallengeDefinition definition : file.challenges()) {
			ApiContract contract = toContract(definition);
			byChallenge.put(contract.getChallengeId(), contract);
			byDevice.putIfAbsent(contract.getDeviceId(), contract);
			byMission.computeIfAbsent(contract.getMissionId(), ignored -> new ArrayList<>()).add(contract);
		}
	}

	public Optional<ApiContract> findByChallengeId(String challengeId) {
		return Optional.ofNullable(contractsByChallengeId.get(challengeId));
	}

	public Optional<ApiContract> findByDeviceId(String deviceId) {
		return Optional.ofNullable(primaryByDeviceId.get(deviceId));
	}

	public ApiContract requireByChallengeId(String challengeId) {
		return findByChallengeId(challengeId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown challengeId: " + challengeId));
	}

	public ApiContract requireByDeviceId(String deviceId) {
		return findByDeviceId(deviceId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown deviceId: " + deviceId));
	}

	public List<ApiContract> findByMissionId(String missionId) {
		return byMissionId.getOrDefault(missionId, List.of());
	}

	public List<ApiContract> allContracts() {
		return List.copyOf(contractsByChallengeId.values());
	}

	public boolean isUngradedContextMission(String missionId) {
		return missionId != null
				&& (missionId.startsWith("m0-") || missionId.startsWith("practice-"));
	}

	private static ApiContract toContract(ChallengeDefinition definition) {
		return new ApiContract(
				definition.challengeId(),
				definition.variantId(),
				definition.missionId(),
				definition.deviceId(),
				definition.conceptTags(),
				definition.expectedMethod(),
				definition.pathTemplate(),
				definition.pathParameters() == null ? Map.of() : definition.pathParameters(),
				definition.queryParameters() == null ? Map.of() : definition.queryParameters(),
				definition.successStatus(),
				definition.successBody(),
				definition.documentation(),
				definition.deviceUnlockState());
	}
}
