package edu.citu.apispy.validation;

import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.domain.simulation.ApiRequest;
import edu.citu.apispy.domain.simulation.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class MethodValidator implements RequestValidator {

	@Override
	public Set<ErrorCode> validate(ApiRequest request, ApiContract contract) {
		if (contract.getExpectedMethod().equalsIgnoreCase(request.getMethod())) {
			return Set.of();
		}
		return Set.of(ErrorCode.METHOD);
	}
}
