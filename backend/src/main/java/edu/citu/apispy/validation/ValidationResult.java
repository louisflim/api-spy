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

	private ValidationResult(Set<ErrorCode> errorCodes) {
		this.errorCodes = Collections.unmodifiableSet(Objects.requireNonNull(errorCodes));
	}

	public static ValidationResult success() {
		return new ValidationResult(EnumSet.noneOf(ErrorCode.class));
	}

	public static ValidationResult failure(Set<ErrorCode> errorCodes) {
		if (errorCodes == null || errorCodes.isEmpty()) {
			throw new IllegalArgumentException("failure requires at least one error code");
		}
		return new ValidationResult(EnumSet.copyOf(errorCodes));
	}

	public boolean isCorrect() {
		return errorCodes.isEmpty();
	}

	public Set<ErrorCode> getErrorCodes() {
		return errorCodes;
	}
}
