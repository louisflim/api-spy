package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptStage;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.domain.simulation.ApiRequest;
import edu.citu.apispy.validation.MethodValidator;
import edu.citu.apispy.validation.PathParameterValidator;
import edu.citu.apispy.validation.PathValidator;
import edu.citu.apispy.validation.QueryParameterValidator;
import edu.citu.apispy.validation.SimulatedRequestValidator;
import edu.citu.apispy.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class LearningPersistenceIntegrationTest {

	@Autowired
	private FirstAttemptService firstAttemptService;

	@Autowired
	private ConceptProgressService conceptProgressService;

	private final SimulatedRequestValidator validator = new SimulatedRequestValidator(List.of(
			new MethodValidator(),
			new PathValidator(),
			new PathParameterValidator(),
			new QueryParameterValidator()));

	@BeforeEach
	void clear() {
		firstAttemptService.clearAll();
		conceptProgressService.clearAll();
	}

	@Test
	void firstAttemptPersistsImmutablyAcrossRetries() {
		String playerId = UUID.randomUUID().toString();
		ApiContract contract = doorContract();

		ValidationResult wrong = validator.validate(
				new ApiRequest("POST", "/api/doors/alpha", Map.of(), ""),
				contract);
		FirstAttemptService.CaptureResult first = firstAttemptService.captureIfAbsent(
				playerId,
				contract,
				new ApiRequest("POST", "/api/doors/alpha", Map.of(), ""),
				wrong);
		assertTrue(first.newlyRecorded());
		assertFalse(first.record().isFirstAttemptCorrect());

		ValidationResult ok = validator.validate(
				new ApiRequest("GET", "/api/doors/alpha", Map.of(), ""),
				contract);
		FirstAttemptService.CaptureResult second = firstAttemptService.captureIfAbsent(
				playerId,
				contract,
				new ApiRequest("GET", "/api/doors/alpha", Map.of(), ""),
				ok);
		assertFalse(second.newlyRecorded());
		assertEquals("POST", second.record().getMethod());
		assertFalse(second.record().isFirstAttemptCorrect());

		assertEquals(1, firstAttemptService.findAllForParticipant(playerId).size());
	}

	@Test
	void conceptStagesUpgradeOnlyAndPersist() {
		String playerId = UUID.randomUUID().toString();
		conceptProgressService.markIntroduced(playerId, "http.method.get");
		conceptProgressService.markPracticing(playerId, "http.method.get");
		conceptProgressService.markImproving(playerId, "http.method.get");
		conceptProgressService.markIntroduced(playerId, "http.method.get");

		assertEquals(
				ConceptStage.IMPROVING,
				conceptProgressService.allForParticipant(playerId).get("http.method.get").stage());
		assertTrue(conceptProgressService.hasAtLeast(playerId, "http.method.get", ConceptStage.INTRODUCED));
	}

	private static ApiContract doorContract() {
		return new ApiContract(
				"chal-m1-get-door-v1",
				"door-alpha-v1",
				"m1-recruit-l1",
				"door-alpha",
				List.of("http.method.get", "http.endpoint"),
				"GET",
				"/api/doors/alpha",
				Map.of(),
				Map.of(),
				200,
				Map.of("ok", true),
				"docs",
				"OPEN");
	}
}
