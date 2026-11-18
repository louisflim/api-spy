package edu.citu.apispy.service.research;

import edu.citu.apispy.domain.learning.FirstAttemptRecord;
import edu.citu.apispy.domain.research.ResearchEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure KPI computation over the hidden research log and immutable first-attempt records
 * (proposal "Primary Performance Evaluation"; SRS UC-07 / NFR-P3). Nothing here changes data.
 *
 * <ul>
 *   <li>Attempt: one room generation of a PRIMARY run that the agent entered (or acted in).
 *       Replays are review variants and are excluded from every KPI.</li>
 *   <li>SCR: distinct missions completed / distinct missions attempted (per participant).</li>
 *   <li>RCA: eligible mandatory decisions correct on first submission / all eligible decisions.
 *       Final locks are excluded; terminals opened but never submitted count as incorrect;
 *       devices never reached do not count.</li>
 *   <li>HLCR: SCR restricted to Special Agent Levels 5-7.</li>
 *   <li>ARC: (API-driven resets + voluntary restarts + abandoned attempts) / missions attempted.
 *       Combat restarts are reported separately so action skill is not read as API difficulty.</li>
 *   <li>Abandonment: attempts left with no closing event for longer than {@link #ABANDON_AFTER}.</li>
 * </ul>
 */
public final class KpiCalculator {

	public static final Duration ABANDON_AFTER = Duration.ofMinutes(30);
	public static final int MISSIONS_PER_RANK = 21;
	public static final List<String> RANKS = List.of("RECRUIT", "FIELD_AGENT", "SPECIAL_AGENT");
	private static final Pattern GRADED = Pattern.compile("^m([1-3])-(recruit|field|special)-l([1-7])$");

	private KpiCalculator() {
	}

	/** Pseudonymous participant code shown in reports and exports (never the account id or name). */
	public static String participantLabel(String participantCode) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(participantCode.getBytes(StandardCharsets.UTF_8));
			return "P-" + HexFormat.of().formatHex(hash).substring(0, 8).toUpperCase(Locale.ROOT);
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	record MissionKey(int module, String rank, int level) {
	}

	static MissionKey parse(String missionId) {
		Matcher m = GRADED.matcher(missionId == null ? "" : missionId);
		if (!m.matches()) {
			return null;
		}
		String rank = switch (m.group(2)) {
			case "recruit" -> "RECRUIT";
			case "field" -> "FIELD_AGENT";
			default -> "SPECIAL_AGENT";
		};
		return new MissionKey(Integer.parseInt(m.group(1)), rank, Integer.parseInt(m.group(3)));
	}

	/** Module where a concept is first taught (Module 1 HTTP basics, 2 JSON/headers/responses, 3 auth/workflows). */
	static int conceptModule(String tag) {
		String t = tag == null ? "" : tag.toLowerCase(Locale.ROOT);
		if (t.startsWith("json") || t.startsWith("http.header") || t.startsWith("http.status")
				|| t.startsWith("http.response")) {
			return 2;
		}
		if (t.startsWith("https") || t.startsWith("auth") || t.startsWith("workflow")) {
			return 3;
		}
		return 1;
	}

	enum Outcome { COMPLETED, API_RESET, VOLUNTARY_RESTART, COMBAT_RESTART, ABANDONED, IN_PROGRESS }

	static final class Attempt {
		String participant;
		String missionId;
		MissionKey key;
		String runKind;
		boolean entered;
		Instant start;
		Instant last;
		Outcome outcome;
		int combatDeaths;
		boolean bossDefeated;
		final Set<String> openedDevices = new HashSet<>();
	}

	/** Builds attempts from events (one per participant + mission + room generation). */
	static List<Attempt> attempts(List<ResearchEvent> events, Instant now) {
		Map<String, Attempt> byGeneration = new LinkedHashMap<>();
		Map<String, Attempt> latestByMission = new HashMap<>();
		for (ResearchEvent e : events) {
			MissionKey key = parse(e.missionId());
			if (key == null || e.roomGeneration() == null) {
				continue;
			}
			String id = e.participantCode() + "|" + e.missionId() + "|" + e.roomGeneration();
			Attempt a = byGeneration.computeIfAbsent(id, ignored -> {
				Attempt created = new Attempt();
				created.participant = e.participantCode();
				created.missionId = e.missionId();
				created.key = key;
				created.runKind = e.runKind();
				created.start = e.occurredAt();
				return created;
			});
			a.last = e.occurredAt();
			switch (e.type()) {
				case ResearchEvent.ROOM_ENTERED -> a.entered = true;
				case ResearchEvent.TERMINAL_OPENED -> {
					a.entered = true;
					if (e.deviceId() != null) {
						a.openedDevices.add(e.deviceId());
					}
				}
				case ResearchEvent.COMBAT_DEATH -> {
					a.entered = true;
					a.combatDeaths++;
				}
				case ResearchEvent.BOSS_DEFEATED -> {
					a.entered = true;
					a.bossDefeated = true;
				}
				case ResearchEvent.ROOM_COMPLETE -> {
					a.entered = true;
					a.outcome = Outcome.COMPLETED;
				}
				case ResearchEvent.ROOM_RESET -> {
					a.entered = true;
					if (a.outcome == null) {
						a.outcome = switch (String.valueOf(e.reason())) {
							case "API_STALLED" -> Outcome.API_RESET;
							case "COMBAT" -> Outcome.COMBAT_RESTART;
							default -> Outcome.VOLUNTARY_RESTART;
						};
					}
				}
				default -> {
				}
			}
			latestByMission.put(e.participantCode() + "|" + e.missionId(), a);
		}
		for (Attempt a : byGeneration.values()) {
			if (a.outcome == null) {
				boolean latest = latestByMission.get(a.participant + "|" + a.missionId) == a;
				boolean fresh = a.last != null && Duration.between(a.last, now).compareTo(ABANDON_AFTER) < 0;
				a.outcome = latest && fresh ? Outcome.IN_PROGRESS : Outcome.ABANDONED;
			}
		}
		return new ArrayList<>(byGeneration.values());
	}

	record Decision(String participant, MissionKey key, boolean correct, Map<String, Boolean> concepts, boolean submitted) {
	}

	/** RCA decisions: first submissions plus opened-but-unsubmitted mandatory terminals (counted incorrect). */
	static List<Decision> decisions(List<FirstAttemptRecord> records, List<Attempt> attempts) {
		List<Decision> out = new ArrayList<>();
		Set<String> submitted = new HashSet<>();
		for (FirstAttemptRecord r : records) {
			MissionKey key = parse(r.getMissionId());
			if (key == null || r.getDeviceId() == null || r.getDeviceId().startsWith("final-lock-")) {
				continue;
			}
			submitted.add(r.getParticipantCode() + "|" + r.getMissionId() + "|" + r.getDeviceId());
			Map<String, Boolean> concepts = r.getConceptFirstAttemptCorrect() == null
					? Map.of()
					: r.getConceptFirstAttemptCorrect();
			out.add(new Decision(r.getParticipantCode(), key, r.isFirstAttemptCorrect(), concepts, true));
		}
		Set<String> unsubmitted = new HashSet<>();
		for (Attempt a : attempts) {
			if (!"PRIMARY".equals(a.runKind)) {
				continue;
			}
			for (String device : a.openedDevices) {
				String id = a.participant + "|" + a.missionId + "|" + device;
				if (!device.startsWith("final-lock-") && !submitted.contains(id) && unsubmitted.add(id)) {
					out.add(new Decision(a.participant, a.key, false, Map.of(), false));
				}
			}
		}
		return out;
	}

	static Map<String, Object> rate(int numerator, int denominator) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("numerator", numerator);
		m.put("denominator", denominator);
		m.put("percent", denominator == 0 ? null : Math.round(1000.0 * numerator / denominator) / 10.0);
		return m;
	}

	static Map<String, Object> ratio(int numerator, int denominator) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("numerator", numerator);
		m.put("denominator", denominator);
		m.put("value", denominator == 0 ? null : Math.round(100.0 * numerator / denominator) / 100.0);
		return m;
	}

	/** Aggregates one report cell (module x rank, module total, or a mission). */
	static final class Cell {
		final Set<String> participants = new HashSet<>();
		final Set<String> missionsAttempted = new HashSet<>();
		final Set<String> missionsCompleted = new HashSet<>();
		final Set<String> hardAttempted = new HashSet<>();
		final Set<String> hardCompleted = new HashSet<>();
		int attempts;
		int finished;
		int completions;
		int apiResets;
		int voluntaryRestarts;
		int combatRestarts;
		int abandoned;
		int inProgress;
		int combatDeaths;
		int bossAttempts;
		int bossDefeats;
		int bossDeaths;
		int rcaCorrect;
		int rcaTotal;
		int unsubmitted;

		void add(Attempt a) {
			String mission = a.participant + "|" + a.missionId;
			participants.add(a.participant);
			missionsAttempted.add(mission);
			boolean hard = "SPECIAL_AGENT".equals(a.key.rank()) && a.key.level() >= 5;
			if (hard) {
				hardAttempted.add(mission);
			}
			attempts++;
			combatDeaths += a.combatDeaths;
			if (a.key.level() == 7) {
				bossAttempts++;
				bossDeaths += a.combatDeaths;
				if (a.bossDefeated) {
					bossDefeats++;
				}
			}
			switch (a.outcome) {
				case COMPLETED -> {
					completions++;
					missionsCompleted.add(mission);
					if (hard) {
						hardCompleted.add(mission);
					}
				}
				case API_RESET -> apiResets++;
				case VOLUNTARY_RESTART -> voluntaryRestarts++;
				case COMBAT_RESTART -> combatRestarts++;
				case ABANDONED -> abandoned++;
				case IN_PROGRESS -> inProgress++;
			}
			if (a.outcome != Outcome.IN_PROGRESS) {
				finished++;
			}
		}

		void add(Decision d) {
			rcaTotal++;
			if (d.correct()) {
				rcaCorrect++;
			}
			if (!d.submitted()) {
				unsubmitted++;
			}
		}

		Map<String, Object> report(boolean hlcrApplies) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("SCR", rate(missionsCompleted.size(), missionsAttempted.size()));
			r.put("RCA", rate(rcaCorrect, rcaTotal));
			r.put("HLCR", hlcrApplies ? rate(hardCompleted.size(), hardAttempted.size()) : null);
			r.put("ARC", ratio(apiResets + voluntaryRestarts + abandoned, missionsAttempted.size()));
			r.put("abandonment", rate(abandoned, finished));
			Map<String, Object> s = new LinkedHashMap<>();
			s.put("participants", participants.size());
			s.put("attempts", attempts);
			s.put("inProgress", inProgress);
			s.put("missionsAttempted", missionsAttempted.size());
			s.put("missionsCompleted", missionsCompleted.size());
			s.put("completions", completions);
			s.put("apiResets", apiResets);
			s.put("voluntaryRestarts", voluntaryRestarts);
			s.put("combatRestarts", combatRestarts);
			s.put("abandoned", abandoned);
			s.put("combatDeaths", combatDeaths);
			s.put("bossAttempts", bossAttempts);
			s.put("bossDefeats", bossDefeats);
			s.put("bossCombatDeaths", bossDeaths);
			s.put("unsubmittedDecisions", unsubmitted);
			r.put("summary", s);
			return r;
		}
	}

	/**
	 * @param participantLabelFilter optional "P-XXXXXXXX" code; null/blank = every participant
	 */
	public static Map<String, Object> compute(
			List<ResearchEvent> events,
			List<FirstAttemptRecord> records,
			String participantLabelFilter,
			Instant now) {
		String filter = participantLabelFilter == null || participantLabelFilter.isBlank()
				? null
				: participantLabelFilter.trim().toUpperCase(Locale.ROOT);
		List<ResearchEvent> ev = filter == null ? events
				: events.stream().filter(e -> participantLabel(e.participantCode()).equals(filter)).toList();
		List<FirstAttemptRecord> rec = filter == null ? records
				: records.stream().filter(r -> participantLabel(r.getParticipantCode()).equals(filter)).toList();

		List<Attempt> all = attempts(ev, now);
		List<Attempt> primary = all.stream().filter(a -> "PRIMARY".equals(a.runKind) && a.entered).toList();
		List<Decision> decisions = decisions(rec, all);

		Map<Integer, Map<String, Cell>> cells = new TreeMap<>();
		for (int m = 1; m <= 3; m++) {
			Map<String, Cell> row = new LinkedHashMap<>();
			for (String rank : RANKS) {
				row.put(rank, new Cell());
			}
			row.put("ALL", new Cell());
			cells.put(m, row);
		}
		Map<String, Cell> missions = new TreeMap<>(Comparator
				.comparing((String id) -> parse(id).module())
				.thenComparing(id -> RANKS.indexOf(parse(id).rank()))
				.thenComparing(id -> parse(id).level()));
		for (Attempt a : primary) {
			Map<String, Cell> row = cells.get(a.key.module());
			row.get(a.key.rank()).add(a);
			row.get("ALL").add(a);
			missions.computeIfAbsent(a.missionId, ignored -> new Cell()).add(a);
		}
		for (Decision d : decisions) {
			Map<String, Cell> row = cells.get(d.key().module());
			row.get(d.key().rank()).add(d);
			row.get("ALL").add(d);
		}

		Map<String, Object> out = new LinkedHashMap<>();
		Set<String> people = new HashSet<>();
		primary.forEach(a -> people.add(a.participant));
		decisions.forEach(d -> people.add(d.participant()));
		out.put("generatedAt", now.toString());
		out.put("participantFilter", filter);
		out.put("participants", people.size());
		out.put("hasData", !primary.isEmpty() || !decisions.isEmpty());

		// RCA overall + breakdowns (descriptive; concept rows use submitted decisions only).
		int correct = (int) decisions.stream().filter(Decision::correct).count();
		Map<String, Object> rca = new LinkedHashMap<>();
		rca.put("overall", rate(correct, decisions.size()));
		rca.put("unsubmittedCountedIncorrect", (int) decisions.stream().filter(d -> !d.submitted()).count());
		Map<String, int[]> byConcept = new TreeMap<>();
		int[] firstUse = new int[2];
		int[] recall = new int[2];
		for (Decision d : decisions) {
			for (Map.Entry<String, Boolean> c : d.concepts().entrySet()) {
				int[] slot = byConcept.computeIfAbsent(c.getKey(), ignored -> new int[2]);
				slot[1]++;
				int[] bucket = d.key().module() > conceptModule(c.getKey()) ? recall : firstUse;
				bucket[1]++;
				if (Boolean.TRUE.equals(c.getValue())) {
					slot[0]++;
					bucket[0]++;
				}
			}
		}
		Map<String, Object> concepts = new LinkedHashMap<>();
		byConcept.forEach((tag, v) -> concepts.put(tag, rate(v[0], v[1])));
		rca.put("byConcept", concepts);
		rca.put("firstUse", rate(firstUse[0], firstUse[1]));
		rca.put("laterModuleRecall", rate(recall[0], recall[1]));
		out.put("rca", rca);

		List<Map<String, Object>> moduleRows = new ArrayList<>();
		cells.forEach((module, row) -> {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("module", module);
			m.put("total", row.get("ALL").report(true));
			Map<String, Object> ranks = new LinkedHashMap<>();
			for (String rank : RANKS) {
				ranks.put(rank, row.get(rank).report("SPECIAL_AGENT".equals(rank)));
			}
			m.put("ranks", ranks);
			moduleRows.add(m);
		});
		out.put("modules", moduleRows);

		Map<String, Object> progression = new LinkedHashMap<>();
		for (String rank : RANKS) {
			Map<String, Set<String>> done = new HashMap<>();
			Set<String> started = new HashSet<>();
			for (Attempt a : primary) {
				if (!rank.equals(a.key.rank())) {
					continue;
				}
				started.add(a.participant);
				if (a.outcome == Outcome.COMPLETED) {
					done.computeIfAbsent(a.participant, ignored -> new HashSet<>()).add(a.missionId);
				}
			}
			int completed = done.values().stream().mapToInt(Set::size).sum();
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("participantsStarted", started.size());
			r.put("participantsFinishedRank", (int) done.values().stream().filter(s -> s.size() >= MISSIONS_PER_RANK).count());
			r.put("completion", rate(completed, MISSIONS_PER_RANK * started.size()));
			progression.put(rank, r);
		}
		out.put("rankProgression", progression);

		List<Map<String, Object>> missionRows = new ArrayList<>();
		missions.forEach((id, cell) -> {
			MissionKey key = parse(id);
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("missionId", id);
			r.put("module", key.module());
			r.put("rank", key.rank());
			r.put("level", key.level());
			r.putAll(cell.report(false));
			r.remove("HLCR");
			r.remove("RCA");
			missionRows.add(r);
		});
		out.put("missions", missionRows);
		return out;
	}

	/** Per-participant overview for the console (pseudonymous codes only). */
	public static List<Map<String, Object>> participants(
			List<ResearchEvent> events,
			List<FirstAttemptRecord> records,
			Instant now) {
		Map<String, Map<String, Object>> rows = new TreeMap<>();
		List<Attempt> primary = attempts(events, now).stream()
				.filter(a -> "PRIMARY".equals(a.runKind) && a.entered)
				.toList();
		for (Attempt a : primary) {
			Map<String, Object> row = rows.computeIfAbsent(participantLabel(a.participant), KpiCalculator::emptyRow);
			row.put("attempts", (int) row.get("attempts") + 1);
			if (a.outcome == Outcome.COMPLETED) {
				row.put("completions", (int) row.get("completions") + 1);
			}
			Instant first = (Instant) row.get("firstSeen");
			Instant last = (Instant) row.get("lastSeen");
			if (first == null || a.start.isBefore(first)) {
				row.put("firstSeen", a.start);
			}
			if (last == null || a.last.isAfter(last)) {
				row.put("lastSeen", a.last);
			}
		}
		for (FirstAttemptRecord r : records) {
			if (parse(r.getMissionId()) == null) {
				continue;
			}
			Map<String, Object> row = rows.computeIfAbsent(participantLabel(r.getParticipantCode()), KpiCalculator::emptyRow);
			row.put("decisions", (int) row.get("decisions") + 1);
		}
		List<Map<String, Object>> out = new ArrayList<>();
		rows.forEach((label, row) -> {
			Map<String, Object> copy = new LinkedHashMap<>(row);
			copy.put("participant", label);
			copy.computeIfPresent("firstSeen", (k, v) -> v.toString());
			copy.computeIfPresent("lastSeen", (k, v) -> v.toString());
			out.add(copy);
		});
		return out;
	}

	private static Map<String, Object> emptyRow(String ignored) {
		Map<String, Object> row = new HashMap<>();
		row.put("attempts", 0);
		row.put("completions", 0);
		row.put("decisions", 0);
		row.put("firstSeen", null);
		row.put("lastSeen", null);
		return row;
	}
}
