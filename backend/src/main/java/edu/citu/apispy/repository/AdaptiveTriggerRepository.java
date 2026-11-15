package edu.citu.apispy.repository;

import edu.citu.apispy.domain.learning.AdaptiveTriggerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdaptiveTriggerRepository extends JpaRepository<AdaptiveTriggerEntity, UUID> {

	Optional<AdaptiveTriggerEntity> findByParticipantCodeAndConceptTagAndEvidenceKey(
			String participantCode,
			String conceptTag,
			String evidenceKey);

	List<AdaptiveTriggerEntity> findByParticipantCodeAndRoomMissionId(
			String participantCode,
			String roomMissionId);

	long countByParticipantCodeAndRoomMissionId(String participantCode, String roomMissionId);
}
