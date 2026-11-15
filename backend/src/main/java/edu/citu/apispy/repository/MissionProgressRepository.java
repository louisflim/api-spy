package edu.citu.apispy.repository;

import edu.citu.apispy.domain.mission.MissionProgressEntity;
import edu.citu.apispy.domain.mission.MissionProgressStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MissionProgressRepository extends JpaRepository<MissionProgressEntity, UUID> {

	Optional<MissionProgressEntity> findByParticipantCodeAndMissionId(String participantCode, String missionId);

	List<MissionProgressEntity> findByParticipantCode(String participantCode);

	List<MissionProgressEntity> findByParticipantCodeAndStatus(String participantCode, MissionProgressStatus status);
}
