package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptStage;

import java.time.Instant;
import java.util.Map;

public interface ConceptProgressStore {

	record ProgressRow(String participantCode, String conceptId, ConceptStage stage, Instant updatedAt) {
	}

	ProgressRow upsert(String participantCode, String conceptId, ConceptStage desired, boolean allowUpgradeOnly);

	Map<String, ProgressRow> allForParticipant(String participantCode);

	void clearAll();
}
