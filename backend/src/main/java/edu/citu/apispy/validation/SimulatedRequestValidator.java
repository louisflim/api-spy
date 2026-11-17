package edu.citu.apispy.validation;

import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.domain.simulation.ApiRequest;
import edu.citu.apispy.domain.simulation.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Composes independent RequestValidator implementations.
 * Does not execute player-entered URLs.
 */
@Component
public class SimulatedRequestValidator {

	private final List<RequestValidator> validators;

	public SimulatedRequestValidator(List<RequestValidator> validators) {
		this.validators = List.copyOf(validators);
	}

	public ValidationResult validate(ApiRequest request, ApiContract contract) {
		Set<ErrorCode> errors = EnumSet.noneOf(ErrorCode.class);
		boolean pathParameterFailed = false;
		boolean queryParameterFailed = false;
		for (RequestValidator validator : validators) {
			Set<ErrorCode> found = validator.validate(request, contract);
			errors.addAll(found);
			if (!found.isEmpty() && validator instanceof PathParameterValidator) {
				pathParameterFailed = true;
			}
			if (!found.isEmpty() && validator instanceof QueryParameterValidator) {
				queryParameterFailed = true;
			}
		}
		return errors.isEmpty()
				? ValidationResult.success()
				: ValidationResult.failure(errors, pathParameterFailed, queryParameterFailed);
	}
}
