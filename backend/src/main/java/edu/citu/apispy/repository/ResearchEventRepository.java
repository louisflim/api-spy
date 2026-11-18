package edu.citu.apispy.repository;

import edu.citu.apispy.domain.research.ResearchEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResearchEventRepository extends JpaRepository<ResearchEventEntity, UUID> {
	List<ResearchEventEntity> findAllByOrderByOccurredAtAsc();
}
