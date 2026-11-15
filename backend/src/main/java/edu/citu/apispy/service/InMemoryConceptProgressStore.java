package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptStage;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryConceptProgressStore implements ConceptProgressStore {

	private final ConcurrentHashMap<String, ProgressRow> progress = new ConcurrentHashMap<>();

	@Override
	public ProgressRow upsert(
			String participantCode,
			String conceptId,
			ConceptStage desired,
			boolean allowUpgradeOnly) {
		String key = participantCode + "|" + conceptId;
		return progress.compute(key, (ignored, existing) -> {
			if (existing == null) {
				return new ProgressRow(participantCode, conceptId, desired, Instant.now());
			}
			if (ordinal(desired) < ordinal(existing.stage())) {
				return existing;
			}
			return new ProgressRow(participantCode, conceptId, desired, Instant.now());
		});
	}

	@Override
	public Map<String, ProgressRow> allForParticipant(String participantCode) {
		ConcurrentHashMap<String, ProgressRow> result = new ConcurrentHashMap<>();
		progress.forEach((key, value) -> {
			if (value.participantCode().equals(participantCode)) {
				result.put(value.conceptId(), value);
			}
		});
		return Map.copyOf(result);
	}

	@Override
	public void clearAll() {
		progress.clear();
	}

	private static int ordinal(ConceptStage stage) {
		return switch (stage) {
			case INTRODUCED -> 1;
			case PRACTICING -> 2;
			case IMPROVING -> 3;
		};
	}
}
