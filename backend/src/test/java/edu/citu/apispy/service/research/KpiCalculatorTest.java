package edu.citu.apispy.service.research;

import edu.citu.apispy.domain.learning.FirstAttemptRecord;
import edu.citu.apispy.domain.research.ResearchEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hand-checkable KPI scenarios (proposal formulas; SRS NFR-L5 for RCA). */
class KpiCalculatorTest {

	private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
	private final List<ResearchEvent> events = new ArrayList<>();
	private final List<FirstAttemptRecord> records = new ArrayList<>();

	private UUID attempt(String who, String mission, String runKind, Instant start, String... types) {
		UUID gen = UUID.randomUUID();
		event(who, mission, gen, ResearchEvent.ROOM_START, null, null, runKind, start);
		Instant t = start;
		for (String type : types) {
			t = t.plusSeconds(30);
			String reason = null;
			String realType = type;
			if (type.startsWith("RESET:")) {
				realType = ResearchEvent.ROOM_RESET;
				reason = type.substring(6);
			}
			event(who, mission, gen, realType, reason, null, runKind, t);
		}
		return gen;
	}

	private void event(String who, String mission, UUID gen, String type, String reason, String device, String runKind, Instant at) {
		KpiCalculator.MissionKey key = KpiCalculator.parse(mission);
		events.add(new ResearchEvent(UUID.randomUUID(), who, mission, key.module(), key.rank(), key.level(), gen,
				type, reason, device, runKind, 0, at));
	}

	private void firstAttempt(String who, String mission, String device, boolean correct, Map<String, Boolean> concepts) {
		records.add(new FirstAttemptRecord(who, mission, device, "chal-" + device, "v1", List.copyOf(concepts.keySet()),
				"GET", "/api/x", Map.of(), "", correct ? List.of() : List.of("METHOD"), correct, concepts, NOW.minusSeconds(60)));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> m(Object o, Object... path) {
		Object cur = o;
		for (Object p : path) {
			cur = p instanceof Integer i ? ((List<Object>) cur).get(i) : ((Map<String, Object>) cur).get(p);
		}
		return (Map<String, Object>) cur;
	}

	private static int num(Map<String, Object> rate) {
		return (int) rate.get("numerator");
	}

	private static int den(Map<String, Object> rate) {
		return (int) rate.get("denominator");
	}

	@Test
	void scrCountsDistinctMissionsPerParticipantAndOnlyEnteredPrimaryRuns() {
		Instant old = NOW.minus(Duration.ofHours(2));
		// alice: stalls once, then clears L1 (2 attempts, 1 mission, completed)
		attempt("alice", "m1-recruit-l1", "PRIMARY", old, ResearchEvent.ROOM_ENTERED, "RESET:API_STALLED");
		attempt("alice", "m1-recruit-l1", "PRIMARY", old.plusSeconds(600), ResearchEvent.ROOM_ENTERED, ResearchEvent.ROOM_COMPLETE);
		// bob: enters L1 and walks away (abandoned after 30 min of silence)
		attempt("bob", "m1-recruit-l1", "PRIMARY", old, ResearchEvent.ROOM_ENTERED);
		// carol: only opened the training screen (progress row, never entered) -> not attempted
		attempt("carol", "m1-recruit-l1", "PRIMARY", old);
		// alice replays L1 later -> review variant, excluded from KPIs
		attempt("alice", "m1-recruit-l1", "REPLAY", old.plusSeconds(1200), ResearchEvent.ROOM_ENTERED, "RESET:API_STALLED");

		Map<String, Object> report = KpiCalculator.compute(events, records, null, NOW);
		Map<String, Object> recruit = m(report, "modules", 0, "ranks", "RECRUIT");
		Map<String, Object> scr = m(recruit, "SCR");
		assertEquals(1, num(scr));
		assertEquals(2, den(scr), "alice + bob attempted; carol never entered");
		Map<String, Object> summary = m(recruit, "summary");
		assertEquals(3, summary.get("attempts"));
		assertEquals(1, summary.get("apiResets"), "replay reset excluded");
		assertEquals(1, summary.get("abandoned"));
		// ARC = (1 API reset + 1 abandoned) / 2 missions attempted
		assertEquals(1.0, m(recruit, "ARC").get("value"));
		// Abandonment = 1 abandoned / 3 finished attempts
		assertEquals(33.3, m(recruit, "abandonment").get("percent"));
		assertNull(recruit.get("HLCR"), "HLCR only for Special Agent");
	}

	@Test
	void latestRecentAttemptIsInProgressNotAbandoned() {
		attempt("dana", "m1-recruit-l2", "PRIMARY", NOW.minusSeconds(120), ResearchEvent.ROOM_ENTERED);
		Map<String, Object> summary = m(KpiCalculator.compute(events, records, null, NOW), "modules", 0, "ranks", "RECRUIT", "summary");
		assertEquals(1, summary.get("inProgress"));
		assertEquals(0, summary.get("abandoned"));
	}

	@Test
	void combatRestartsAreSeparateFromApiDifficulty() {
		Instant t = NOW.minus(Duration.ofHours(1));
		attempt("eve", "m1-recruit-l3", "PRIMARY", t, ResearchEvent.ROOM_ENTERED, ResearchEvent.COMBAT_DEATH, "RESET:COMBAT");
		attempt("eve", "m1-recruit-l3", "PRIMARY", t.plusSeconds(300), ResearchEvent.ROOM_ENTERED, ResearchEvent.ROOM_COMPLETE);
		Map<String, Object> recruit = m(KpiCalculator.compute(events, records, null, NOW), "modules", 0, "ranks", "RECRUIT");
		assertEquals(0, m(recruit, "ARC").get("numerator"), "combat restarts are not API retries");
		assertEquals(1, m(recruit, "summary").get("combatRestarts"));
		assertEquals(1, m(recruit, "summary").get("combatDeaths"));
	}

	@Test
	void hlcrUsesSpecialAgentLevelsFiveToSevenAndBossOutcomes() {
		Instant t = NOW.minus(Duration.ofHours(1));
		attempt("finn", "m1-special-l5", "PRIMARY", t, ResearchEvent.ROOM_ENTERED, ResearchEvent.ROOM_COMPLETE);
		attempt("finn", "m1-special-l7", "PRIMARY", t, ResearchEvent.ROOM_ENTERED, ResearchEvent.COMBAT_DEATH, "RESET:COMBAT");
		attempt("finn", "m1-special-l7", "PRIMARY", t.plusSeconds(400), ResearchEvent.ROOM_ENTERED,
				ResearchEvent.BOSS_DEFEATED, ResearchEvent.ROOM_COMPLETE);
		attempt("finn", "m1-special-l2", "PRIMARY", t, ResearchEvent.ROOM_ENTERED, "RESET:API_STALLED");
		Map<String, Object> special = m(KpiCalculator.compute(events, records, null, NOW), "modules", 0, "ranks", "SPECIAL_AGENT");
		Map<String, Object> hlcr = m(special, "HLCR");
		assertEquals(2, num(hlcr));
		assertEquals(2, den(hlcr), "L2 is not a hard level");
		Map<String, Object> summary = m(special, "summary");
		assertEquals(2, summary.get("bossAttempts"));
		assertEquals(1, summary.get("bossDefeats"));
		assertEquals(1, summary.get("bossCombatDeaths"));
	}

	@Test
	void rcaUsesFirstSubmissionsExcludesFinalLocksAndCountsOpenedUnsubmittedAsIncorrect() {
		Instant t = NOW.minus(Duration.ofHours(1));
		UUID gen = attempt("gia", "m1-recruit-l1", "PRIMARY", t, ResearchEvent.ROOM_ENTERED);
		event("gia", "m1-recruit-l1", gen, ResearchEvent.TERMINAL_OPENED, null, "door-alpha", "PRIMARY", t.plusSeconds(40));
		event("gia", "m1-recruit-l1", gen, ResearchEvent.TERMINAL_OPENED, null, "door-bravo", "PRIMARY", t.plusSeconds(50));
		firstAttempt("gia", "m1-recruit-l1", "door-alpha", true, Map.of("http.method.get", true, "http.endpoint", true));
		firstAttempt("gia", "m1-recruit-l1", "final-lock-l1", false, Map.of("http.method.delete", false));
		firstAttempt("gia", "practice-m1-recruit-l1", "practice-panel", false, Map.of("http.method.get", false));
		// door-bravo was opened but never submitted -> incorrect; door-charlie never reached -> not counted

		Map<String, Object> report = KpiCalculator.compute(events, records, null, NOW);
		Map<String, Object> overall = m(report, "rca", "overall");
		assertEquals(1, num(overall));
		assertEquals(2, den(overall), "door-alpha correct + door-bravo unsubmitted");
		assertEquals(1, m(report, "rca").get("unsubmittedCountedIncorrect"));
		assertEquals(100.0, m(report, "rca", "byConcept", "http.method.get").get("percent"));
		assertFalse(m(report, "rca", "byConcept").containsKey("http.method.delete"), "final lock excluded");
		assertEquals(2, den(m(report, "modules", 0, "ranks", "RECRUIT", "RCA")));
	}

	@Test
	void participantFilterUsesPseudonymousCodes() {
		Instant t = NOW.minus(Duration.ofHours(1));
		attempt("hana", "m1-recruit-l1", "PRIMARY", t, ResearchEvent.ROOM_ENTERED, ResearchEvent.ROOM_COMPLETE);
		attempt("ivan", "m1-recruit-l1", "PRIMARY", t, ResearchEvent.ROOM_ENTERED);
		String code = KpiCalculator.participantLabel("hana");
		assertTrue(code.matches("P-[0-9A-F]{8}"));
		Map<String, Object> report = KpiCalculator.compute(events, records, code.toLowerCase(), NOW);
		assertEquals(1, report.get("participants"));
		assertEquals(1, den(m(report, "modules", 0, "ranks", "RECRUIT", "SCR")));
		List<Map<String, Object>> people = KpiCalculator.participants(events, records, NOW);
		assertEquals(2, people.size());
		assertTrue(people.stream().noneMatch(p -> p.get("participant").equals("hana")), "raw ids never exposed");
	}

	@Test
	void emptyLogReportsNoData() {
		Map<String, Object> report = KpiCalculator.compute(List.of(), List.of(), null, NOW);
		assertEquals(false, report.get("hasData"));
		assertNull(m(report, "modules", 0, "total", "SCR").get("percent"));
	}

	@Test
	void csvCellsAreEscapedAndFormulaSafe() {
		assertEquals("\"a,b\"", ResearchReportService.escape("a,b"));
		assertEquals("\"say \"\"hi\"\"\"", ResearchReportService.escape("say \"hi\""));
		assertEquals("'=HYPERLINK(1)", ResearchReportService.escape("=HYPERLINK(1)"));
		assertEquals("", ResearchReportService.escape(null));
	}
}
