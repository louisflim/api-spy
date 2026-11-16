package edu.citu.apispy.repository;

import edu.citu.apispy.domain.learning.PracticeOutcomeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PracticeOutcomeRepository extends JpaRepository<PracticeOutcomeEntity, UUID> {

	List<PracticeOutcomeEntity> findByParticipantCodeOrderByCreatedAtDesc(String participantCode);
}
