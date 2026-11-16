package edu.citu.apispy.service;

import tools.jackson.databind.ObjectMapper;
import edu.citu.apispy.content.ContentLoader;
import edu.citu.apispy.domain.mission.MissionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionCatalogServiceTest {

	@Test
	void buildsSixtyThreeConfigurationsWithSevenPlayable() {
		MissionCatalogService service = new MissionCatalogService(new ContentLoader(new ObjectMapper()));

		assertEquals(63, service.totalConfigured());
		assertEquals(7, service.implementedCount());
		assertEquals(7, service.playableMissions().size());
		assertTrue(service.requirePlayable("m1-recruit-l1").isPlayable());
		assertTrue(service.requirePlayable("m1-recruit-l4").isPlayable());
		assertTrue(service.requirePlayable("m1-recruit-l5").isPlayable());
		assertTrue(service.requirePlayable("m1-recruit-l6").isPlayable());
		assertTrue(service.requirePlayable("m1-recruit-l7").isPlayable());
		assertEquals(MissionStatus.IMPLEMENTED, service.findById("m1-recruit-l7").orElseThrow().getStatus());
		assertEquals(MissionStatus.PLANNED, service.findById("m3-special-l7").orElseThrow().getStatus());
		assertThrows(IllegalArgumentException.class, () -> service.requirePlayable("m3-special-l7"));
		assertFalse(service.findById("m2-field-l4").orElseThrow().isPlayable());
	}
}
