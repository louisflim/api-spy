package edu.citu.apispy.repository;

import edu.citu.apispy.domain.learning.FirstAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FirstAttemptRecordRepository extends JpaRepository<FirstAttemptEntity, UUID> {

	Optional<FirstAttemptEntity> findByParticipantCodeAndMissionIdAndDeviceId(
			String participantCode,
			String missionId,
			String deviceId);

	List<FirstAttemptEntity> findByParticipantCodeOrderBySubmittedAtAsc(String participantCode);

	boolean existsByParticipantCodeAndMissionIdAndDeviceId(
			String participantCode,
			String missionId,
			String deviceId);
}
