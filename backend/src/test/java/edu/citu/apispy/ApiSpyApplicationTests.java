package edu.citu.apispy;

import edu.citu.apispy.config.AdaptiveLearningProperties;
import edu.citu.apispy.service.ContractCatalogService;
import edu.citu.apispy.service.LessonContentProvider;
import edu.citu.apispy.service.MissionCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class ApiSpyApplicationTests {

	@Autowired
	private AdaptiveLearningProperties adaptiveLearningProperties;

	@Autowired
	private MissionCatalogService missionCatalogService;

	@Autowired
	private ContractCatalogService contractCatalogService;

	@Autowired
	private LessonContentProvider lessonContentProvider;

	@Test
	void contextLoadsWithDayTwoContent() {
		assertEquals(2, adaptiveLearningProperties.getIncorrectAttemptThreshold());
		assertEquals(2, adaptiveLearningProperties.getMaxPracticeDoorsPerRoom());
		assertEquals(63, missionCatalogService.totalConfigured());
		// Module 1: Recruit, Field Agent and Special Agent tracks (7 levels each).
		assertEquals(21, missionCatalogService.implementedCount());
		assertFalse(contractCatalogService.allContracts().isEmpty());
		assertEquals(9, lessonContentProvider.byModule(1).size());
	}
}
