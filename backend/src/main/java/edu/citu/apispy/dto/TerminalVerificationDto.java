package edu.citu.apispy.dto;

/** Player-facing terminal verification progress (not an academic grade). */
public record TerminalVerificationDto(
		int percent,
		int earnedSlots,
		int requiredSlots,
		String terminalId,
		boolean complete,
		VerifyAnotherScenarioDto verifyAnotherScenario
) {
}
