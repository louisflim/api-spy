package edu.citu.apispy.service.research;

import edu.citu.apispy.domain.learning.FirstAttemptRecord;
import edu.citu.apispy.domain.research.ResearchEvent;
import edu.citu.apispy.service.FirstAttemptStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only research reporting for the administrator console (SRS UC-07).
 * Viewing or exporting never changes gameplay records.
 */
@Service
public class ResearchReportService {

	private final ResearchEventLog events;
	private final FirstAttemptStore firstAttempts;
	private final Clock clock;

	@Autowired
	public ResearchReportService(ResearchEventLog events, FirstAttemptStore firstAttempts) {
		this(events, firstAttempts, Clock.systemUTC());
	}

	ResearchReportService(ResearchEventLog events, FirstAttemptStore firstAttempts, Clock clock) {
		this.events = events;
		this.firstAttempts = firstAttempts;
		this.clock = clock;
	}

	public Map<String, Object> kpi(String participantLabel) {
		return KpiCalculator.compute(events.all(), firstAttempts.findAll(), participantLabel, Instant.now(clock));
	}

	public List<Map<String, Object>> participants() {
		return KpiCalculator.participants(events.all(), firstAttempts.findAll(), Instant.now(clock));
	}

	/** Event log as CSV (pseudonymous participant codes only). */
	public String eventsCsv() {
		StringBuilder csv = new StringBuilder(
				"participant,mission_id,module,rank,level,room_generation,event_type,reason,device_id,run_kind,api_reset_count,occurred_at\n");
		for (ResearchEvent e : events.all()) {
			row(csv, KpiCalculator.participantLabel(e.participantCode()), e.missionId(), e.module(), e.rank(), e.level(),
					e.roomGeneration(), e.type(), e.reason(), e.deviceId(), e.runKind(), e.apiResetCount(), e.occurredAt());
		}
		return csv.toString();
	}

	/**
	 * Immutable first submissions as CSV. Request headers and bodies are deliberately omitted
	 * (NFR-S4: never export anything a student might have typed as a real secret).
	 */
	public String firstAttemptsCsv() {
		StringBuilder csv = new StringBuilder(
				"participant,mission_id,device_id,challenge_id,variant_id,concept_tags,method,path,error_codes,first_attempt_correct,concept_correct,submitted_at\n");
		for (FirstAttemptRecord r : firstAttempts.findAll()) {
			String concepts = r.getConceptFirstAttemptCorrect() == null ? ""
					: r.getConceptFirstAttemptCorrect().entrySet().stream()
							.map(c -> c.getKey() + "=" + c.getValue())
							.collect(Collectors.joining(";"));
			row(csv, KpiCalculator.participantLabel(r.getParticipantCode()), r.getMissionId(), r.getDeviceId(),
					r.getChallengeId(), r.getVariantId(), String.join(";", r.getConceptTags()), r.getMethod(), r.getPath(),
					String.join(";", r.getErrorCodes()), r.isFirstAttemptCorrect(), concepts, r.getSubmittedAt());
		}
		return csv.toString();
	}

	private static void row(StringBuilder csv, Object... cells) {
		for (int i = 0; i < cells.length; i++) {
			if (i > 0) {
				csv.append(',');
			}
			csv.append(escape(cells[i]));
		}
		csv.append('\n');
	}

	static String escape(Object value) {
		if (value == null) {
			return "";
		}
		String s = value.toString();
		// Neutralise spreadsheet formula injection from student-typed paths.
		if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) {
			s = "'" + s;
		}
		if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
			s = "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}
}
