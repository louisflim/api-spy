package edu.citu.apispy.service;

import edu.citu.apispy.content.ContentLoader;
import edu.citu.apispy.content.LessonDefinition;
import edu.citu.apispy.content.LessonFile;
import edu.citu.apispy.domain.learning.Lesson;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Serves ungraded explanations and worked examples. No grading during instruction.
 */
@Service
public class LessonContentProvider {

	private final Map<String, Lesson> lessonsById;

	public LessonContentProvider(ContentLoader contentLoader) {
		LessonFile file = contentLoader.load("content/lessons/module1-lessons.json", LessonFile.class);
		Map<String, Lesson> lessons = new LinkedHashMap<>();
		file.lessons().stream()
				.sorted(Comparator.comparingInt(LessonDefinition::order))
				.forEach(definition -> lessons.put(definition.id(), toLesson(definition)));
		this.lessonsById = Map.copyOf(lessons);
	}

	public List<Lesson> allLessons() {
		return lessonsById.values().stream()
				.sorted(Comparator.comparingInt(Lesson::getOrder))
				.toList();
	}

	public List<Lesson> byModule(int module) {
		return allLessons().stream().filter(lesson -> lesson.getModule() == module).toList();
	}

	public Optional<Lesson> findById(String lessonId) {
		return Optional.ofNullable(lessonsById.get(lessonId));
	}

	public Lesson requireById(String lessonId) {
		return findById(lessonId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown lessonId: " + lessonId));
	}

	private static Lesson toLesson(LessonDefinition definition) {
		return new Lesson(
				definition.id(),
				definition.module(),
				definition.order(),
				definition.title(),
				definition.conceptTags(),
				definition.explanation(),
				new Lesson.WorkedExample(
						definition.workedExample().title(),
						definition.workedExample().notes(),
						definition.workedExample().method(),
						definition.workedExample().path(),
						definition.workedExample().responseStatus(),
						definition.workedExample().responseBody()),
				new Lesson.PracticeScenario(
						definition.practiceScenario().summary(),
						definition.practiceScenario().linkedMissionId(),
						definition.practiceScenario().ungraded()));
	}
}
