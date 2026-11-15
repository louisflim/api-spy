package edu.citu.apispy.repository;

import edu.citu.apispy.domain.learning.ConceptDecisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConceptDecisionRepository extends JpaRepository<ConceptDecisionEntity, UUID> {

	List<ConceptDecisionEntity> findByParticipantCodeAndMissionId(String participantCode, String missionId);
}
