package edu.citu.apispy.service.research;

import edu.citu.apispy.domain.research.ResearchEvent;

import java.util.ArrayList;
import java.util.List;

/** Test / offline log. Production uses {@link JpaResearchEventLog}. */
public final class InMemoryResearchEventLog implements ResearchEventLog {

	private final List<ResearchEvent> events = new ArrayList<>();

	@Override
	public synchronized void append(ResearchEvent event) {
		events.add(event);
	}

	@Override
	public synchronized List<ResearchEvent> all() {
		return List.copyOf(events);
	}
}
