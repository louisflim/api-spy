package edu.citu.apispy.validation;

import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.domain.simulation.ApiRequest;
import edu.citu.apispy.domain.simulation.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParameterValidatorsTest {

	private final SimulatedRequestValidator validator = new SimulatedRequestValidator(List.of(
			new MethodValidator(),
			new PathValidator(),
			new PathParameterValidator(),
			new QueryParameterValidator()));

	@Test
	void detectsMissingAndIncorrectQueryParameter() {
		ApiContract contract = queryContract();

		ValidationResult missing = validator.validate(
				new ApiRequest("GET", "/api/sensors", Map.of(), ""),
				contract);
		assertTrue(missing.getErrorCodes().contains(ErrorCode.PARAMETER_MISSING));

		ValidationResult wrong = validator.validate(
				new ApiRequest("GET", "/api/sensors?zone=wrong", Map.of(), ""),
				contract);
		assertTrue(wrong.getErrorCodes().contains(ErrorCode.PARAMETER_VALUE));

		ValidationResult ok = validator.validate(
				new ApiRequest("GET", "/api/sensors?zone=alpha", Map.of(), ""),
				contract);
		assertTrue(ok.isCorrect());
	}

	@Test
	void detectsPathParameterProblemsIndependentlyFromMethod() {
		ApiContract contract = pathContract();
		ValidationResult result = validator.validate(
				new ApiRequest("POST", "/api/locks/wrong-id", Map.of(), ""),
				contract);

		Set<ErrorCode> codes = result.getErrorCodes();
		assertTrue(codes.contains(ErrorCode.METHOD));
		assertTrue(codes.contains(ErrorCode.PARAMETER_VALUE));
		assertEquals(3, codes.size());
	}

	private static ApiContract queryContract() {
		return new ApiContract(
				"chal-query",
				"v1",
				"m1-recruit-l3",
				"sensor-panel",
				List.of("http.query_parameter"),
				"GET",
				"/api/sensors",
				Map.of(),
				Map.of("zone", "alpha"),
				200,
				Map.of("ok", true),
				"docs",
				"OPEN");
	}

	private static ApiContract pathContract() {
		return new ApiContract(
				"chal-path",
				"v1",
				"m1-recruit-l2",
				"lock-north",
				List.of("http.path_parameter"),
				"GET",
				"/api/locks/{lockId}",
				Map.of("lockId", "north-gate"),
				Map.of(),
				200,
				Map.of("ok", true),
				"docs",
				"OPEN");
	}
}
