package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.FirstAttemptEntity;
import edu.citu.apispy.domain.learning.FirstAttemptRecord;
import edu.citu.apispy.repository.FirstAttemptRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaFirstAttemptStore implements FirstAttemptStore {

	private final FirstAttemptRecordRepository repository;
	private final LearningJsonCodec codec;

	public JpaFirstAttemptStore(FirstAttemptRecordRepository repository, LearningJsonCodec codec) {
		this.repository = repository;
		this.codec = codec;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<FirstAttemptRecord> find(String participantCode, String missionId, String deviceId) {
		return repository
				.findByParticipantCodeAndMissionIdAndDeviceId(participantCode, missionId, deviceId)
				.map(this::toDomain);
	}

	@Override
	@Transactional
	public InsertResult insertIfAbsent(FirstAttemptRecord candidate) {
		Optional<FirstAttemptEntity> existing = repository.findByParticipantCodeAndMissionIdAndDeviceId(
				candidate.getParticipantCode(),
				candidate.getMissionId(),
				candidate.getDeviceId());
		if (existing.isPresent()) {
			return new InsertResult(toDomain(existing.get()), false);
		}

		try {
			FirstAttemptEntity saved = repository.saveAndFlush(toEntity(candidate));
			return new InsertResult(toDomain(saved), true);
		} catch (DataIntegrityViolationException ex) {
			FirstAttemptRecord raced = repository
					.findByParticipantCodeAndMissionIdAndDeviceId(
							candidate.getParticipantCode(),
							candidate.getMissionId(),
							candidate.getDeviceId())
					.map(this::toDomain)
					.orElseThrow(() -> ex);
			return new InsertResult(raced, false);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<FirstAttemptRecord> findAllForParticipant(String participantCode) {
		return repository.findByParticipantCodeOrderBySubmittedAtAsc(participantCode).stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	@Transactional
	public void clearAll() {
		repository.deleteAllInBatch();
	}

	private FirstAttemptEntity toEntity(FirstAttemptRecord record) {
		return new FirstAttemptEntity(
				UUID.randomUUID(),
				record.getParticipantCode(),
				record.getMissionId(),
				record.getDeviceId(),
				record.getChallengeId(),
				record.getVariantId(),
				codec.writeList(record.getConceptTags()),
				record.getMethod(),
				record.getPath(),
				codec.writeStringMap(record.getHeaders()),
				record.getBody(),
				codec.writeList(record.getErrorCodes()),
				record.isFirstAttemptCorrect(),
				codec.writeBooleanMap(record.getConceptFirstAttemptCorrect()),
				record.getSubmittedAt());
	}

	private FirstAttemptRecord toDomain(FirstAttemptEntity entity) {
		return new FirstAttemptRecord(
				entity.getParticipantCode(),
				entity.getMissionId(),
				entity.getDeviceId(),
				entity.getChallengeId(),
				entity.getVariantId(),
				codec.readList(entity.getConceptTagsJson()),
				entity.getMethod(),
				entity.getPath(),
				codec.readStringMap(entity.getHeadersJson()),
				entity.getBody(),
				codec.readList(entity.getErrorCodesJson()),
				entity.isFirstAttemptCorrect(),
				codec.readBooleanMap(entity.getConceptCorrectJson()),
				entity.getSubmittedAt());
	}
}
