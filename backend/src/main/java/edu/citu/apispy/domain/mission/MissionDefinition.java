package edu.citu.apispy.domain.mission;

import java.util.List;
import java.util.Objects;

public final class MissionDefinition {

	private final String missionId;
	private final int module;
	private final Rank rank;
	private final int level;
	private final String title;
	private final MissionStatus status;
	private final String primaryChallengeId;
	private final List<String> requiredLessonIds;
	private final List<String> conceptTags;
	private final String scenarioFocus;

	public MissionDefinition(
			String missionId,
			int module,
			Rank rank,
			int level,
			String title,
			MissionStatus status,
			String primaryChallengeId,
			List<String> requiredLessonIds,
			List<String> conceptTags,
			String scenarioFocus) {
		this.missionId = Objects.requireNonNull(missionId);
		this.module = module;
		this.rank = Objects.requireNonNull(rank);
		this.level = level;
		this.title = Objects.requireNonNull(title);
		this.status = Objects.requireNonNull(status);
		this.primaryChallengeId = primaryChallengeId;
		this.requiredLessonIds = requiredLessonIds == null ? List.of() : List.copyOf(requiredLessonIds);
		this.conceptTags = conceptTags == null ? List.of() : List.copyOf(conceptTags);
		this.scenarioFocus = scenarioFocus == null ? "" : scenarioFocus;
	}

	public String getMissionId() {
		return missionId;
	}

	public int getModule() {
		return module;
	}

	public Rank getRank() {
		return rank;
	}

	public int getLevel() {
		return level;
	}

	public String getTitle() {
		return title;
	}

	public MissionStatus getStatus() {
		return status;
	}

	public boolean isPlayable() {
		return status == MissionStatus.IMPLEMENTED && primaryChallengeId != null && !primaryChallengeId.isBlank();
	}

	public String getPrimaryChallengeId() {
		return primaryChallengeId;
	}

	public List<String> getRequiredLessonIds() {
		return requiredLessonIds;
	}

	public List<String> getConceptTags() {
		return conceptTags;
	}

	public String getScenarioFocus() {
		return scenarioFocus;
	}
}
