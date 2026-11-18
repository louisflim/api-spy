package edu.citu.apispy.service.research;

import edu.citu.apispy.domain.research.ResearchEvent;
import edu.citu.apispy.domain.research.ResearchEventEntity;
import edu.citu.apispy.repository.ResearchEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JpaResearchEventLog implements ResearchEventLog {

	private final ResearchEventRepository repository;

	public JpaResearchEventLog(ResearchEventRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public void append(ResearchEvent event) {
		repository.save(ResearchEventEntity.from(event));
	}

	@Override
	@Transactional(readOnly = true)
	public List<ResearchEvent> all() {
		return repository.findAllByOrderByOccurredAtAsc().stream().map(ResearchEventEntity::toEvent).toList();
	}
}
