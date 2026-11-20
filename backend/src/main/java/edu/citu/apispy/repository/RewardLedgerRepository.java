package edu.citu.apispy.repository;

import edu.citu.apispy.domain.rewards.RewardLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RewardLedgerRepository extends JpaRepository<RewardLedgerEntity, UUID> {

	long countByParticipantCodeAndMissionIdAndRoomGenerationAndKind(
			String participantCode, String missionId, UUID roomGeneration, String kind);

	boolean existsByDedupeKey(String dedupeKey);

	@Query("select coalesce(sum(l.amount), 0) from RewardLedgerEntity l "
			+ "where l.participantCode = :code and l.missionId = :mission and l.kind in :kinds")
	long sumForMission(
			@Param("code") String participantCode,
			@Param("mission") String missionId,
			@Param("kinds") Collection<String> kinds);

	List<RewardLedgerEntity> findByParticipantCodeOrderByOccurredAtAsc(String participantCode);
}
