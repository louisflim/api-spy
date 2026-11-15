package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.TerminalVerificationEvidenceEntity;
import edu.citu.apispy.repository.TerminalVerificationEvidenceRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Primary
@Component
class JpaTerminalVerificationStore implements TerminalVerificationStore {

	private final TerminalVerificationEvidenceRepository repository;

	JpaTerminalVerificationStore(TerminalVerificationEvidenceRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<EvidenceRow> find(
			String participantCode,
			String missionId,
			String challengeId,
			String conceptTag) {
		return repository
				.findByParticipantCodeAndMissionIdAndChallengeIdAndConceptTag(
						participantCode, missionId, challengeId, conceptTag)
				.map(JpaTerminalVerificationStore::toRow);
	}

	@Override
	public EvidenceRow insertIfAbsent(EvidenceRow row) {
		return repository
				.findByParticipantCodeAndMissionIdAndChallengeIdAndConceptTag(
						row.participantCode(), row.missionId(), row.challengeId(), row.conceptTag())
				.map(JpaTerminalVerificationStore::toRow)
				.orElseGet(() -> toRow(repository.save(new TerminalVerificationEvidenceEntity(
						UUID.randomUUID(),
						row.participantCode(),
						row.missionId(),
						row.terminalId(),
						row.challengeId(),
						row.conceptTag(),
						Instant.now()))));
	}

	@Override
	public long countForTerminal(String participantCode, String missionId, String terminalId) {
		return repository.countByParticipantCodeAndMissionIdAndTerminalId(
				participantCode, missionId, terminalId);
	}

	private static EvidenceRow toRow(TerminalVerificationEvidenceEntity entity) {
		return new EvidenceRow(
				entity.getParticipantCode(),
				entity.getMissionId(),
				entity.getTerminalId(),
				entity.getChallengeId(),
				entity.getConceptTag());
	}
}
