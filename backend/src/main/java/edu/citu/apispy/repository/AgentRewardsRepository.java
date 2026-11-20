package edu.citu.apispy.repository;

import edu.citu.apispy.domain.rewards.AgentRewardsEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AgentRewardsRepository extends JpaRepository<AgentRewardsEntity, String> {

	/** Row lock so concurrent grants and spends cannot race past balances or caps. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from AgentRewardsEntity r where r.participantCode = :code")
	Optional<AgentRewardsEntity> findForUpdate(@Param("code") String participantCode);
}
