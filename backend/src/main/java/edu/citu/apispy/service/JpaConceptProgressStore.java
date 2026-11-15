package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptProgressEntity;
import edu.citu.apispy.domain.learning.ConceptStage;
import edu.citu.apispy.repository.ConceptProgressRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JpaConceptProgressStore implements ConceptProgressStore {

	private final ConceptProgressRepository repository;

	public JpaConceptProgressStore(ConceptProgressRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public ProgressRow upsert(
			String participantCode,
			String conceptId,
			ConceptStage desired,
			boolean allowUpgradeOnly) {
		ConceptProgressEntity entity = repository
				.findByParticipantCodeAndConceptId(participantCode, conceptId)
				.orElse(null);

		if (entity == null) {
			entity = new ConceptProgressEntity(
					UUID.randomUUID(),
					participantCode,
					conceptId,
					desired,
					Instant.now());
			repository.save(entity);
			return toRow(entity);
		}

		if (ordinal(desired) < ordinal(entity.getStage())) {
			return toRow(entity);
		}

		entity.setStage(desired);
		entity.setUpdatedAt(Instant.now());
		repository.save(entity);
		return toRow(entity);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, ProgressRow> allForParticipant(String participantCode) {
		Map<String, ProgressRow> result = new LinkedHashMap<>();
		for (ConceptProgressEntity entity : repository.findByParticipantCode(participantCode)) {
			result.put(entity.getConceptId(), toRow(entity));
		}
		return Map.copyOf(result);
	}

	@Override
	@Transactional
	public void clearAll() {
		repository.deleteAllInBatch();
	}

	private static ProgressRow toRow(ConceptProgressEntity entity) {
		return new ProgressRow(
				entity.getParticipantCode(),
				entity.getConceptId(),
				entity.getStage(),
				entity.getUpdatedAt());
	}

	private static int ordinal(ConceptStage stage) {
		return switch (stage) {
			case INTRODUCED -> 1;
			case PRACTICING -> 2;
			case IMPROVING -> 3;
		};
	}
}
