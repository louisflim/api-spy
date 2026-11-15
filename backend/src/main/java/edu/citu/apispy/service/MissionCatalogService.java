package edu.citu.apispy.service;

import edu.citu.apispy.content.ContentLoader;
import edu.citu.apispy.content.ImplementedMissionDefinition;
import edu.citu.apispy.content.ImplementedMissionFile;
import edu.citu.apispy.domain.mission.MissionDefinition;
import edu.citu.apispy.domain.mission.MissionStatus;
import edu.citu.apispy.domain.mission.Rank;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the full 63 mission catalog. Only IMPLEMENTED missions are playable.
 */
@Service
public class MissionCatalogService {

	private static final String[] MODULE_FOCUS = {
			"",
			"HTTP methods, endpoints, and parameters",
			"JSON, headers, and response interpretation",
			"HTTPS, simulated authorization, and linked workflows"
	};

	private final Map<String, MissionDefinition> missionsById;

	public MissionCatalogService(ContentLoader contentLoader) {
		ImplementedMissionFile implementedFile = contentLoader.load(
				"content/missions/implemented-missions.json",
				ImplementedMissionFile.class);

		Map<String, ImplementedMissionDefinition> implemented = new LinkedHashMap<>();
		for (ImplementedMissionDefinition definition : implementedFile.missions()) {
			implemented.put(definition.missionId(), definition);
		}

		Map<String, MissionDefinition> all = new LinkedHashMap<>();
		for (int module = 1; module <= 3; module++) {
			for (Rank rank : Rank.values()) {
				for (int level = 1; level <= 7; level++) {
					String missionId = missionId(module, rank, level);
					ImplementedMissionDefinition override = implemented.get(missionId);
					if (override != null) {
						all.put(missionId, fromImplemented(override));
					} else {
						all.put(missionId, planned(module, rank, level));
					}
				}
			}
		}
		this.missionsById = Map.copyOf(all);
	}

	public List<MissionDefinition> allMissions() {
		return List.copyOf(missionsById.values());
	}

	public List<MissionDefinition> playableMissions() {
		List<MissionDefinition> playable = new ArrayList<>();
		for (MissionDefinition mission : missionsById.values()) {
			if (mission.isPlayable()) {
				playable.add(mission);
			}
		}
		return playable;
	}

	public Optional<MissionDefinition> findById(String missionId) {
		return Optional.ofNullable(missionsById.get(missionId));
	}

	public MissionDefinition requirePlayable(String missionId) {
		MissionDefinition mission = findById(missionId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown missionId: " + missionId));
		if (!mission.isPlayable()) {
			throw new IllegalArgumentException("Mission is planned and not playable yet: " + missionId);
		}
		return mission;
	}

	public int totalConfigured() {
		return missionsById.size();
	}

	public long implementedCount() {
		return missionsById.values().stream().filter(MissionDefinition::isPlayable).count();
	}

	public static String missionId(int module, Rank rank, int level) {
		String rankKey = switch (rank) {
			case RECRUIT -> "recruit";
			case FIELD_AGENT -> "field";
			case SPECIAL_AGENT -> "special";
		};
		return "m" + module + "-" + rankKey + "-l" + level;
	}

	private static MissionDefinition fromImplemented(ImplementedMissionDefinition definition) {
		return new MissionDefinition(
				definition.missionId(),
				definition.module(),
				Rank.valueOf(definition.rank().toUpperCase(Locale.ROOT)),
				definition.level(),
				definition.title(),
				MissionStatus.IMPLEMENTED,
				definition.primaryChallengeId(),
				definition.requiredLessonIds(),
				definition.conceptTags(),
				definition.scenarioFocus());
	}

	private static MissionDefinition planned(int module, Rank rank, int level) {
		String title = "Module " + module + " " + rank.name().replace('_', ' ') + " Level " + level;
		String focus = level == 7
				? "Boss-only final room for " + MODULE_FOCUS[module]
				: "Planned scenario for " + MODULE_FOCUS[module];
		return new MissionDefinition(
				missionId(module, rank, level),
				module,
				rank,
				level,
				title,
				MissionStatus.PLANNED,
				null,
				List.of(),
				List.of(),
				focus);
	}
}
