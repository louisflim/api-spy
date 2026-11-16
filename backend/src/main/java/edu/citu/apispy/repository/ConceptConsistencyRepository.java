package edu.citu.apispy.repository;

import edu.citu.apispy.domain.learning.ConceptConsistencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConceptConsistencyRepository extends JpaRepository<ConceptConsistencyEntity, UUID> {

	List<ConceptConsistencyEntity> findByParticipantCodeAndConceptTagAndStatus(
			String participantCode,
			String conceptTag,
			String status);

	List<ConceptConsistencyEntity> findByParticipantCodeAndMissionIdAndStatus(
			String participantCode,
			String missionId,
			String status);
}
