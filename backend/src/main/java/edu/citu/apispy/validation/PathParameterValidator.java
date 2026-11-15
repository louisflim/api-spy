package edu.citu.apispy.validation;

import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.domain.simulation.ApiRequest;
import edu.citu.apispy.domain.simulation.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class PathParameterValidator implements RequestValidator {

	@Override
	public Set<ErrorCode> validate(ApiRequest request, ApiContract contract) {
		Map<String, String> expected = contract.getPathParameters();
		if (expected.isEmpty()) {
			return Set.of();
		}

		Map<String, String> actual = PathTemplateMatcher.extract(contract.getPathTemplate(), request.getPath());
		EnumSet<ErrorCode> errors = EnumSet.noneOf(ErrorCode.class);

		for (Map.Entry<String, String> entry : expected.entrySet()) {
			String name = entry.getKey();
			String expectedValue = entry.getValue();
			if (!actual.containsKey(name) || actual.get(name) == null || actual.get(name).isBlank()) {
				errors.add(ErrorCode.PARAMETER_MISSING);
				errors.add(ErrorCode.PARAMETER);
				continue;
			}
			if (!expectedValue.equals(actual.get(name))) {
				errors.add(ErrorCode.PARAMETER_VALUE);
				errors.add(ErrorCode.PARAMETER);
			}
		}

		return errors;
	}
}
