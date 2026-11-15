package edu.citu.apispy.repository;

import edu.citu.apispy.domain.learning.ConceptReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConceptReviewRepository extends JpaRepository<ConceptReviewEntity, UUID> {

	List<ConceptReviewEntity> findByParticipantCodeAndStatus(String participantCode, String status);

	List<ConceptReviewEntity> findByParticipantCode(String participantCode);
}
