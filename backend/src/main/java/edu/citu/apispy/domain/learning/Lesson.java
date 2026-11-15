package edu.citu.apispy.domain.learning;

import java.util.List;
import java.util.Objects;

public final class Lesson {

	private final String id;
	private final int module;
	private final int order;
	private final String title;
	private final List<String> conceptTags;
	private final String explanation;
	private final WorkedExample workedExample;
	private final PracticeScenario practiceScenario;

	public Lesson(
			String id,
			int module,
			int order,
			String title,
			List<String> conceptTags,
			String explanation,
			WorkedExample workedExample,
			PracticeScenario practiceScenario) {
		this.id = Objects.requireNonNull(id);
		this.module = module;
		this.order = order;
		this.title = Objects.requireNonNull(title);
		this.conceptTags = List.copyOf(conceptTags);
		this.explanation = Objects.requireNonNull(explanation);
		this.workedExample = Objects.requireNonNull(workedExample);
		this.practiceScenario = Objects.requireNonNull(practiceScenario);
	}

	public String getId() {
		return id;
	}

	public int getModule() {
		return module;
	}

	public int getOrder() {
		return order;
	}

	public String getTitle() {
		return title;
	}

	public List<String> getConceptTags() {
		return conceptTags;
	}

	public String getExplanation() {
		return explanation;
	}

	public WorkedExample getWorkedExample() {
		return workedExample;
	}

	public PracticeScenario getPracticeScenario() {
		return practiceScenario;
	}

	public record WorkedExample(
			String title,
			String notes,
			String method,
			String path,
			int responseStatus,
			Object responseBody
	) {
	}

	public record PracticeScenario(
			String summary,
			String linkedMissionId,
			boolean ungraded
	) {
	}
}
