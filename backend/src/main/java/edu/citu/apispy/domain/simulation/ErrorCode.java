package edu.citu.apispy.domain.simulation;

/**
 * Concept-specific error tags for eligible request decisions.
 * Multiple codes may apply to one submission.
 */
public enum ErrorCode {
	METHOD,
	ENDPOINT,
	PARAMETER,
	PARAMETER_MISSING,
	PARAMETER_VALUE,
	JSON_SYNTAX,
	JSON_FIELDS,
	HEADER,
	AUTH,
	STATUS_USE,
	RESPONSE_VALUE,
	WORKFLOW_ORDER
}
