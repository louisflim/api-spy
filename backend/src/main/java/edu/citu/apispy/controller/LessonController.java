package edu.citu.apispy.controller;

import edu.citu.apispy.domain.learning.Lesson;
import edu.citu.apispy.dto.LessonDto;
import edu.citu.apispy.security.AuthenticatedPlayer;
import edu.citu.apispy.service.ConceptProgressService;
import edu.citu.apispy.service.LessonContentProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

	private final LessonContentProvider lessonContentProvider;
	private final ConceptProgressService conceptProgressService;

	public LessonController(
			LessonContentProvider lessonContentProvider,
			ConceptProgressService conceptProgressService) {
		this.lessonContentProvider = lessonContentProvider;
		this.conceptProgressService = conceptProgressService;
	}

	@GetMapping
	public List<LessonDto> list(@RequestParam(required = false) Integer module) {
		List<Lesson> lessons = module == null
				? lessonContentProvider.allLessons()
				: lessonContentProvider.byModule(module);
		return lessons.stream().map(LessonController::toDto).toList();
	}

	@GetMapping("/{lessonId}")
	public LessonDto get(@PathVariable String lessonId) {
		return toDto(lessonContentProvider.requireById(lessonId));
	}

	@PostMapping("/{lessonId}/introduce")
	public Map<String, Object> introduce(@PathVariable String lessonId) {
		String playerId = AuthenticatedPlayer.requireUserIdString();
		Lesson lesson = lessonContentProvider.requireById(lessonId);
		// Lesson-scoped marker — Module 0 / practice PRACTICING must not clear the pre-mission gate.
		conceptProgressService.markIntroduced(playerId, lessonIntroductionKey(lesson.getId()));
		for (String conceptTag : lesson.getConceptTags()) {
			conceptProgressService.markIntroduced(playerId, conceptTag);
		}
		return Map.of(
				"lessonId", lessonId,
				"playerId", playerId,
				"graded", false,
				"includedInKpis", false,
				"message", "Lesson marked Introduced. Lessons are ungraded and excluded from SCR/RCA/HLCR.");
	}

	/** Stable concept-progress key proving a specific lesson was opened (not only practiced). */
	static String lessonIntroductionKey(String lessonId) {
		return "lesson:" + lessonId;
	}

	private static LessonDto toDto(Lesson lesson) {
		return new LessonDto(
				lesson.getId(),
				lesson.getModule(),
				lesson.getOrder(),
				lesson.getTitle(),
				lesson.getConceptTags(),
				lesson.getExplanation(),
				new LessonDto.WorkedExampleDto(
						lesson.getWorkedExample().title(),
						lesson.getWorkedExample().notes(),
						lesson.getWorkedExample().method(),
						lesson.getWorkedExample().path(),
						lesson.getWorkedExample().responseStatus(),
						lesson.getWorkedExample().responseBody()),
				new LessonDto.PracticeScenarioDto(
						lesson.getPracticeScenario().summary(),
						lesson.getPracticeScenario().linkedMissionId(),
						lesson.getPracticeScenario().ungraded()),
				false);
	}
}
