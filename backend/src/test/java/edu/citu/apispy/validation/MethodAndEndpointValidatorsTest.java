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

class MethodAndEndpointValidatorsTest {

	private final SimulatedRequestValidator validator = new SimulatedRequestValidator(List.of(
			new MethodValidator(),
			new PathValidator(),
			new PathParameterValidator(),
			new QueryParameterValidator()));

	@Test
	void reportsMethodAndEndpointIndependently() {
		ApiContract contract = new ApiContract(
				"chal",
				"v1",
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

		ValidationResult result = validator.validate(
				new ApiRequest("DELETE", "/api/doors/wrong", Map.of(), ""),
				contract);

		Set<ErrorCode> codes = result.getErrorCodes();
		assertTrue(codes.contains(ErrorCode.METHOD));
		assertTrue(codes.contains(ErrorCode.ENDPOINT));
		assertEquals(2, codes.size());
	}
}
