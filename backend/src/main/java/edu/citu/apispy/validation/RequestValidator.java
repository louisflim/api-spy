package edu.citu.apispy.validation;

import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.domain.simulation.ApiRequest;
import edu.citu.apispy.domain.simulation.ErrorCode;

import java.util.Set;

/**
 * Abstraction for one independent request check.
 * Implementations may report zero or more specific error codes.
 */
public interface RequestValidator {

	Set<ErrorCode> validate(ApiRequest request, ApiContract contract);
}
