package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptStage;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks only Introduced, Practicing, and Improving.
 * Lessons are never graded mission attempts.
 */
@Service
public class ConceptProgressService {

	public record ConceptProgressView(
			String participantCode,
			String conceptId,
			ConceptStage stage,
			Instant updatedAt
	) {
	}

	private final ConceptProgressStore store;

	public ConceptProgressService(ConceptProgressStore store) {
		this.store = store;
	}

	/** Unit-test helper that keeps prior in-memory semantics without Spring. */
	public static ConceptProgressService inMemory() {
		return new ConceptProgressService(new InMemoryConceptProgressStore());
	}

	public ConceptProgressView markIntroduced(String participantCode, String conceptId) {
		return toView(store.upsert(participantCode, conceptId, ConceptStage.INTRODUCED, false));
	}

	public ConceptProgressView markPracticing(String participantCode, String conceptId) {
		return toView(store.upsert(participantCode, conceptId, ConceptStage.PRACTICING, true));
	}

	public ConceptProgressView markImproving(String participantCode, String conceptId) {
		return toView(store.upsert(participantCode, conceptId, ConceptStage.IMPROVING, true));
	}

	public Map<String, ConceptProgressView> allForParticipant(String participantCode) {
		Map<String, ConceptProgressView> result = new LinkedHashMap<>();
		store.allForParticipant(participantCode).forEach((conceptId, row) -> result.put(conceptId, toView(row)));
		return Map.copyOf(result);
	}

	public boolean hasAtLeast(String participantCode, String conceptId, ConceptStage minimum) {
		ConceptProgressStore.ProgressRow row = store.allForParticipant(participantCode).get(conceptId);
		if (row == null) {
			return false;
		}
		return ordinal(row.stage()) >= ordinal(minimum);
	}

	public void clearAll() {
		store.clearAll();
	}

	private static ConceptProgressView toView(ConceptProgressStore.ProgressRow row) {
		return new ConceptProgressView(row.participantCode(), row.conceptId(), row.stage(), row.updatedAt());
	}

	private static int ordinal(ConceptStage stage) {
		return switch (stage) {
			case INTRODUCED -> 1;
			case PRACTICING -> 2;
			case IMPROVING -> 3;
		};
	}
}
