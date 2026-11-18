package edu.citu.apispy.service.research;

import edu.citu.apispy.domain.research.ResearchEvent;

import java.util.List;

/** Append-only hidden research log (SRS 2.3: log decisions, completion, reset reason, rank, timing). */
public interface ResearchEventLog {

	void append(ResearchEvent event);

	/** All events, oldest first. */
	List<ResearchEvent> all();

	/** Used when no log is wired (focused unit tests): records nothing. */
	ResearchEventLog NOOP = new ResearchEventLog() {
		@Override
		public void append(ResearchEvent event) {
		}

		@Override
		public List<ResearchEvent> all() {
			return List.of();
		}
	};
}
