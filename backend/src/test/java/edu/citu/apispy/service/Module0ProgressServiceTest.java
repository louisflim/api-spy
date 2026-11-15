package edu.citu.apispy.service;

import edu.citu.apispy.content.ContentLoader;
import edu.citu.apispy.dto.Module0ProgressDto;
import edu.citu.apispy.dto.Module0ProgressUpdateDto;
import edu.citu.apispy.repository.Module0ProgressRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class Module0ProgressServiceTest {

	@Autowired
	private Module0ProgressService progressService;

	@Autowired
	private Module0ProgressRepository repository;

	@BeforeEach
	void clear() {
		repository.deleteAllInBatch();
	}

	@Test
	void cannotCompleteTopicWithoutInteractionStep() {
		String player = UUID.randomUUID().toString();
		assertThrows(IllegalArgumentException.class, () -> progressService.updateForPlayer(
				player,
				new Module0ProgressUpdateDto("welcome", null, true, "welcome")));
	}

	@Test
	void interactionThenCompletePersistsAcrossReload() {
		String player = UUID.randomUUID().toString();
		progressService.updateForPlayer(
				player,
				new Module0ProgressUpdateDto("welcome", "interaction", false, "welcome"));
		Module0ProgressDto completed = progressService.updateForPlayer(
				player,
				new Module0ProgressUpdateDto("welcome", "interaction", true, "welcome"));
		assertTrue(completed.completedTopicIds().contains("welcome"));

		Module0ProgressDto reloaded = progressService.getForPlayer(player);
		assertTrue(reloaded.completedTopicIds().contains("welcome"));
		assertFalse(reloaded.orientationCompleted());
	}

	@Test
	void contentLoadsSixTopics() {
		Module0ContentService content = new Module0ContentService(new ContentLoader(new ObjectMapper()));
		assertTrue(content.allTopicIds().size() >= 6);
	}
}
