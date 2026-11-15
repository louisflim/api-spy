package edu.citu.apispy.service;

import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.SimulateRequestDto;
import edu.citu.apispy.dto.SimulateResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Graded mission docs must teach resources/methods/rules without pasting the completed request.
 * First-eligible submissions remain independently captured per challenge.
 */
class MissionDocumentationIndependenceTest {

	private ContractCatalogService contracts;
	private RequestSimulationService service;
	private FirstAttemptService firstAttempts;

	@BeforeEach
	void setUp() {
		SimulationTestFixtures.Bundle bundle = SimulationTestFixtures.create();
		contracts = bundle.contracts();
		service = bundle.service();
		firstAttempts = bundle.firstAttempts();
	}

	@Test
	void mandatoryMissionDocumentationDoesNotDirectlyExposeCompletedAnswers() {
		List<ApiContract> graded = Stream.of(
						"m1-recruit-l1",
						"m1-recruit-l2",
						"m1-recruit-l3",
						"m1-recruit-l4",
						"m1-recruit-l5",
						"m1-recruit-l6")
				.flatMap(id -> contracts.findByMissionId(id).stream())
				.toList();

		assertFalse(graded.isEmpty());
		for (ApiContract contract : graded) {
			assertFalse(
					contractCatalogServiceIsUngraded(contract.getMissionId()),
					"expected graded mission contract");
			String docs = contract.getDocumentation();
			String lower = docs.toLowerCase(Locale.ROOT);

			assertFalse(
					containsCompletedAnswerLine(docs, contract),
					() -> "Documentation exposes completed answer for " + contract.getChallengeId()
							+ ":\n" + docs);

			// Must still be solvable: teach resource family, method semantics, and parameters.
			assertTrue(
					lower.contains("get") || lower.contains("method"),
					"docs should teach method semantics for " + contract.getChallengeId());
			assertTrue(
					lower.contains("/api/"),
					"docs should describe resource paths for " + contract.getChallengeId());
			assertTrue(
					lower.contains("construct") || lower.contains("build") || lower.contains("choose"),
					"docs should require independent construction for " + contract.getChallengeId());
		}
	}

	@Test
	void trainingAndPracticeDocumentationMayIncludeCompleteWorkedAnswers() {
		ApiContract training = contracts.requireByChallengeId("chal-m0-training-terminal");
		assertTrue(training.getDocumentation().contains("/api/training/lockers/academy"));
		assertTrue(training.getDocumentation().toLowerCase(Locale.ROOT).contains("method: get"));

		ApiContract practice = contracts.requireByChallengeId("chal-practice-m1-l1");
		assertTrue(practice.getDocumentation().contains("/api/practice/doors/drill"));
		assertTrue(practice.getDocumentation().toLowerCase(Locale.ROOT).contains("method: get"));
	}

	@Test
	void firstEligibleSubmissionsAreCapturedIndependentlyPerChallenge() {
		SimulateResponseDto doorWrong = service.simulate(
				"agent-independent",
				request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "POST", "/api/doors/alpha"));
		assertTrue(doorWrong.firstAttemptRecorded());
		assertFalse(doorWrong.firstAttemptRecord().firstAttemptCorrect());

		SimulateResponseDto doorRetry = service.simulate(
				"agent-independent",
				request("m1-recruit-l1", "door-alpha", "chal-m1-get-door-v1", "GET", "/api/doors/alpha"));
		assertFalse(doorRetry.firstAttemptRecorded());
		assertFalse(doorRetry.firstAttemptRecord().firstAttemptCorrect());
		assertTrue(doorRetry.correct());

		SimulateResponseDto lockFirst = service.simulate(
				"agent-independent",
				request(
						"m1-recruit-l2",
						"lock-north",
						"chal-m1-path-lock-v1",
						"GET",
						"/api/locks/north-gate"));
		assertTrue(lockFirst.firstAttemptRecorded());
		assertTrue(lockFirst.firstAttemptRecord().firstAttemptCorrect());

		assertTrue(firstAttempts.find("agent-independent", "m1-recruit-l1", "door-alpha").isPresent());
		assertTrue(firstAttempts.find("agent-independent", "m1-recruit-l2", "lock-north").isPresent());
		assertFalse(
				firstAttempts
						.find("agent-independent", "m1-recruit-l1", "door-alpha")
						.orElseThrow()
						.isFirstAttemptCorrect());
		assertTrue(
				firstAttempts
						.find("agent-independent", "m1-recruit-l2", "lock-north")
						.orElseThrow()
						.isFirstAttemptCorrect());
	}

	private static boolean contractCatalogServiceIsUngraded(String missionId) {
		return missionId.startsWith("m0-") || missionId.startsWith("practice-");
	}

	private static boolean containsCompletedAnswerLine(String docs, ApiContract contract) {
		String expectedPath = contract.getPathTemplate();
		for (Map.Entry<String, String> entry : contract.getPathParameters().entrySet()) {
			expectedPath = expectedPath.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		if (!contract.getQueryParameters().isEmpty()) {
			StringBuilder query = new StringBuilder();
			for (Map.Entry<String, String> entry : contract.getQueryParameters().entrySet()) {
				if (!query.isEmpty()) {
					query.append('&');
				}
				query.append(entry.getKey()).append('=').append(entry.getValue());
			}
			expectedPath = expectedPath + "?" + query;
		}

		String method = contract.getExpectedMethod();
		String compact = docs.replace("\r\n", "\n");
		// Direct "Method: GET" + "Path: <exact answer>" pairing is forbidden for graded missions.
		if (compact.matches("(?s)(?i).*Method:\\s*" + method + "\\s*\\n\\s*Path(?: template)?:\\s*"
				+ java.util.regex.Pattern.quote(expectedPath) + ".*")) {
			return true;
		}
		if (compact.toLowerCase(Locale.ROOT).contains("example: " + expectedPath.toLowerCase(Locale.ROOT))) {
			return true;
		}
		if (compact.toLowerCase(Locale.ROOT).contains("example shape: " + expectedPath.toLowerCase(Locale.ROOT))) {
			return true;
		}
		// Fully assembled answer path printed as the solution line.
		return compact.contains("Path: " + expectedPath)
				|| compact.contains("Path template: " + expectedPath);
	}

	private static SimulateRequestDto request(
			String missionId,
			String deviceId,
			String challengeId,
			String method,
			String path) {
		return new SimulateRequestDto(
				"participant",
				missionId,
				deviceId,
				challengeId,
				method,
				path,
				Map.of(),
				"",
				"MISSION");
	}
}
