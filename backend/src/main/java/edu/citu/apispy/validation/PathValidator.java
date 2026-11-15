package edu.citu.apispy.validation;

import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.domain.simulation.ApiRequest;
import edu.citu.apispy.domain.simulation.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates resource-path structure against an authored template.
 * Path-parameter values are checked by {@link PathParameterValidator}.
 */
@Component
public class PathValidator implements RequestValidator {

	@Override
	public Set<ErrorCode> validate(ApiRequest request, ApiContract contract) {
		Pattern pattern = PathTemplateMatcher.toPattern(contract.getPathTemplate());
		if (pattern.matcher(request.getPath()).matches()) {
			return Set.of();
		}
		return Set.of(ErrorCode.ENDPOINT);
	}
}
