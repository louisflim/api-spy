package edu.citu.apispy.repository;

import edu.citu.apispy.domain.learning.Module0ProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface Module0ProgressRepository extends JpaRepository<Module0ProgressEntity, UUID> {

	Optional<Module0ProgressEntity> findByParticipantCode(String participantCode);
}
