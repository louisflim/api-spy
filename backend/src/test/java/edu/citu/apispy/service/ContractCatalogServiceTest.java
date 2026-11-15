package edu.citu.apispy.service;

import tools.jackson.databind.ObjectMapper;
import edu.citu.apispy.content.ContentLoader;
import edu.citu.apispy.domain.simulation.ApiContract;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractCatalogServiceTest {

	@Test
	void loadsAuthoredModuleOneAndTrainingChallengesFromJson() {
		ContractCatalogService service = new ContractCatalogService(new ContentLoader(new ObjectMapper()));

		// Module 1 graded/escalation/final-lock + Module 0/practice contracts
		assertEquals(46, service.allContracts().size());
		ApiContract door = service.requireByChallengeId("chal-m1-get-door-v1");
		assertEquals("door-alpha", door.getDeviceId());
		assertEquals("GET", door.getExpectedMethod());

		assertTrue(service.findByMissionId("m1-recruit-l1").size() >= 2);
		assertFalse(service.findByMissionId("m1-recruit-l2").isEmpty());
		assertTrue(service.findByChallengeId("chal-m0-training-terminal").isPresent());
		assertTrue(service.isUngradedContextMission("practice-m1-recruit-l1"));
	}
}
