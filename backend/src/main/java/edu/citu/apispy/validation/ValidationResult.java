package edu.citu.apispy.validation;

import edu.citu.apispy.domain.simulation.ErrorCode;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregated concept-level validation outcome for one simulated request.
 */
public final class ValidationResult {

	private final Set<ErrorCode> errorCodes;
	/**
	 * PARAMETER* codes are shared by the path- and query-parameter validators; these flags
	 * record which kind actually failed so concept attribution (RCA breakdown, adaptive
	 * review) does not blame path parameters for a missing query string or vice versa.
	 */
	private final boolean pathParameterFailed;
	private final boolean queryParameterFailed;

	private ValidationResult(Set<ErrorCode> errorCodes, boolean pathParameterFailed, boolean queryParameterFailed) {
		this.errorCodes = Collections.unmodifiableSet(Objects.requireNonNull(errorCodes));
		this.pathParameterFailed = pathParameterFailed;
		this.queryParameterFailed = queryParameterFailed;
	}

	public static ValidationResult success() {
		return new ValidationResult(EnumSet.noneOf(ErrorCode.class), false, false);
	}

	/** Source of the parameter failure unknown: treat both parameter kinds as failed (legacy). */
	public static ValidationResult failure(Set<ErrorCode> errorCodes) {
		boolean parameterError = errorCodes != null && errorCodes.stream().anyMatch(ValidationResult::isParameterCode);
		return failure(errorCodes, parameterError, parameterError);
	}

	public static ValidationResult failure(
			Set<ErrorCode> errorCodes,
			boolean pathParameterFailed,
			boolean queryParameterFailed) {
		if (errorCodes == null || errorCodes.isEmpty()) {
			throw new IllegalArgumentException("failure requires at least one error code");
		}
		return new ValidationResult(EnumSet.copyOf(errorCodes), pathParameterFailed, queryParameterFailed);
	}

	public static boolean isParameterCode(ErrorCode code) {
		return code == ErrorCode.PARAMETER || code == ErrorCode.PARAMETER_MISSING || code == ErrorCode.PARAMETER_VALUE;
	}

	public boolean pathParameterFailed() {
		return pathParameterFailed;
	}

	public boolean queryParameterFailed() {
		return queryParameterFailed;
	}

	public boolean isCorrect() {
		return errorCodes.isEmpty();
	}

	public Set<ErrorCode> getErrorCodes() {
		return errorCodes;
	}
}
