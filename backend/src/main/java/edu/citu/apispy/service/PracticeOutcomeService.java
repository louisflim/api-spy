package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.PracticeOutcomeEntity;
import edu.citu.apispy.dto.PracticeOutcomeDto;
import edu.citu.apispy.repository.PracticeOutcomeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persists ungraded practice outcomes separately from graded first-attempt / TVP evidence.
 * A single correct practice attempt never marks Improving.
 */
@Service
public class PracticeOutcomeService {

	private final PracticeOutcomeRepository repository;
	private final List<PracticeOutcomeEntity> memoryRows;
	private final boolean memoryMode;

	@Autowired
	public PracticeOutcomeService(PracticeOutcomeRepository repository) {
		this.repository = repository;
		this.memoryRows = null;
		this.memoryMode = false;
	}

	private PracticeOutcomeService() {
		this.repository = null;
		this.memoryRows = new CopyOnWriteArrayList<>();
		this.memoryMode = true;
	}

	public static PracticeOutcomeService inMemory() {
		return new PracticeOutcomeService();
	}

	public PracticeOutcomeDto record(
			String participantCode,
			String conceptTag,
			String challengeId,
			String roomMissionId,
			boolean correct) {
		Instant now = Instant.now();
		PracticeOutcomeEntity entity = new PracticeOutcomeEntity(
				UUID.randomUUID(),
				participantCode,
				conceptTag,
				challengeId,
				roomMissionId,
				correct,
				now);
		if (memoryMode) {
			memoryRows.add(entity);
		} else {
			repository.save(entity);
		}
		return new PracticeOutcomeDto(
				conceptTag,
				challengeId,
				roomMissionId,
				correct,
				now.toString());
	}

	public List<PracticeOutcomeDto> forParticipant(String participantCode) {
		List<PracticeOutcomeDto> items = new ArrayList<>();
		if (memoryMode) {
			for (PracticeOutcomeEntity entity : memoryRows) {
				if (entity.getParticipantCode().equals(participantCode)) {
					items.add(toDto(entity));
				}
			}
			return List.copyOf(items);
		}
		return repository.findByParticipantCodeOrderByCreatedAtDesc(participantCode).stream()
				.map(PracticeOutcomeService::toDto)
				.toList();
	}

	private static PracticeOutcomeDto toDto(PracticeOutcomeEntity entity) {
		return new PracticeOutcomeDto(
				entity.getConceptTag(),
				entity.getChallengeId(),
				entity.getRoomMissionId(),
				entity.isCorrect(),
				entity.getCreatedAt().toString());
	}
}
