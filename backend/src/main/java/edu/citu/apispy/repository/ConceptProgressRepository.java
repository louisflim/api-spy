package edu.citu.apispy.repository;

import edu.citu.apispy.domain.learning.ConceptProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConceptProgressRepository extends JpaRepository<ConceptProgressEntity, UUID> {

	Optional<ConceptProgressEntity> findByParticipantCodeAndConceptId(String participantCode, String conceptId);

	List<ConceptProgressEntity> findByParticipantCode(String participantCode);
}
