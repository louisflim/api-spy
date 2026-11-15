package edu.citu.apispy.domain.learning;

import java.util.List;

/** One concept-level outcome from a single simulate submission. */
public record ConceptDecision(
		String conceptTag,
		boolean applicable,
		boolean correct,
		List<String> errorCodes
) {
}
