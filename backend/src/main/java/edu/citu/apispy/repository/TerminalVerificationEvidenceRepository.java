package edu.citu.apispy.repository;

import edu.citu.apispy.domain.learning.TerminalVerificationEvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TerminalVerificationEvidenceRepository
		extends JpaRepository<TerminalVerificationEvidenceEntity, UUID> {

	List<TerminalVerificationEvidenceEntity> findByParticipantCodeAndMissionIdAndTerminalId(
			String participantCode,
			String missionId,
			String terminalId);

	Optional<TerminalVerificationEvidenceEntity>
			findByParticipantCodeAndMissionIdAndChallengeIdAndConceptTag(
					String participantCode,
					String missionId,
					String challengeId,
					String conceptTag);

	long countByParticipantCodeAndMissionIdAndTerminalId(
			String participantCode,
			String missionId,
			String terminalId);
}
