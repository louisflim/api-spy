package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptDecision;
import edu.citu.apispy.domain.simulation.SimulationMode;
import edu.citu.apispy.dto.AdaptivePracticeDoorDto;
import edu.citu.apispy.dto.ControlFeedbackDto;
import edu.citu.apispy.validation.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Authored CONTROL feedback. Never reveals the active mission answer path.
 */
@Service
public class AgentFeedbackService {

	public ControlFeedbackDto build(
			ValidationResult result,
			List<ConceptDecision> decisions,
			boolean firstEligible,
			boolean firstAttemptCorrect,
			SimulationMode mode) {
		List<String> messages = new ArrayList<>();
		Set<String> tags = new LinkedHashSet<>();
		String tone;

		if (result.isCorrect()) {
			tone = "success";
			if (firstEligible && firstAttemptCorrect) {
				messages.add(
						"Excellent work, Agent. Your request satisfies the required contract. "
								+ "Terminal verification evidence was recorded for this challenge.");
			} else if (!firstEligible) {
				messages.add(
						"Mission access granted. Retry recorded — prior first-attempt evidence is preserved "
								+ "and does not award new verification slots. Optional Concept Verification "
								+ "can add eligible evidence from a distinct challenge.");
			} else {
				messages.add("Mission access granted. Contract requirements are satisfied.");
			}
			for (ConceptDecision decision : decisions) {
				if (decision.applicable() && !decision.correct()) {
					tone = "warn";
					tags.add(decision.conceptTag());
					messages.add(
							"Mission access granted. However, one request detail requires further attention: "
									+ friendlyConcept(decision.conceptTag()) + ".");
				}
			}
		} else {
			tone = "warn";
			boolean mentionedMethod = false;
			boolean mentionedEndpoint = false;
			boolean mentionedParameter = false;
			for (ConceptDecision decision : decisions) {
				if (!decision.applicable() || decision.correct()) {
					continue;
				}
				tags.add(decision.conceptTag());
				if (decision.errorCodes().contains("METHOD") && !mentionedMethod) {
					messages.add("Agent, the selected HTTP method does not match the operation requested.");
					mentionedMethod = true;
				}
				if (decision.errorCodes().contains("ENDPOINT") && !mentionedEndpoint) {
					messages.add(
							"Your request reached the wrong fictional resource. Review how endpoints identify resources.");
					mentionedEndpoint = true;
				}
				if ((decision.errorCodes().contains("PARAMETER")
						|| decision.errorCodes().contains("PARAMETER_MISSING")
						|| decision.errorCodes().contains("PARAMETER_VALUE"))
						&& !mentionedParameter) {
					messages.add(
							"Control has detected difficulty with request parameters. Review path and query usage.");
					mentionedParameter = true;
				}
			}
			if (messages.isEmpty()) {
				messages.add("Agent, the request does not match the documented fictional contract. Review the terminal brief.");
			}
			if (mode == SimulationMode.MISSION) {
				messages.add("Revise this same challenge and resubmit. The complete correct request is never revealed.");
			} else if (mode == SimulationMode.PRACTICE || mode == SimulationMode.TRAINING) {
				messages.add(
						"Training note: compare method, resource path, and required parameters with the drill documentation. "
								+ "Exact live-mission answers are never revealed here.");
			}
		}

		return new ControlFeedbackDto(tone, List.copyOf(messages), List.copyOf(tags));
	}

	/**
	 * Appends CONTROL messaging for optional recurring-difficulty field doors only.
	 * Wrong graded answers do not auto-assign practice or mini-lessons.
	 */
	public ControlFeedbackDto withTargetedSupport(
			ControlFeedbackDto base,
			List<AdaptivePracticeDoorDto> practiceDoors) {
		return withPracticeSupport(base, practiceDoors, null, List.of());
	}

	public ControlFeedbackDto withPracticeSupport(
			ControlFeedbackDto base,
			List<AdaptivePracticeDoorDto> practiceDoors,
			AdaptivePracticeDoorDto autoPracticeOffer) {
		return withPracticeSupport(base, practiceDoors, autoPracticeOffer, List.of());
	}

	public ControlFeedbackDto withPracticeSupport(
			ControlFeedbackDto base,
			List<AdaptivePracticeDoorDto> practiceDoors,
			AdaptivePracticeDoorDto autoPracticeOffer,
			List<ConceptConsistencyService.ObservationOutcome> consistencyOutcomes) {
		if (base == null) {
			return null;
		}
		List<String> messages = new ArrayList<>(base.messages());
		Set<String> tags = new LinkedHashSet<>(base.conceptTags());
		// Physical adaptive practice doors are retired; practiceDoors / autoPracticeOffer ignored.
		if (consistencyOutcomes != null) {
			for (ConceptConsistencyService.ObservationOutcome outcome : consistencyOutcomes) {
				if (outcome == null) {
					continue;
				}
				tags.add(outcome.conceptTag());
				String concept = ConceptSupportCatalog.friendlyConcept(outcome.conceptTag());
				if (outcome.opened()) {
					messages.add(
							"CONTROL will observe " + concept
									+ " on later distinct graded scenarios. Correcting this same challenge "
									+ "does not count as independent improvement evidence.");
				} else if (outcome.improved()) {
					messages.add(
							"Consistent improvement noted for " + concept
									+ " after consecutive correct first attempts on distinct scenarios. "
									+ "This is not a validated mastery claim.");
				} else if (outcome.exhausted()) {
					messages.add(
							"Observation limit reached for " + concept
									+ ". Queued for later review. Mandatory exit is not blocked.");
				} else if (outcome.counted() && outcome.consecutiveSuccesses() > 0) {
					messages.add(
							"Independent correct observation for " + concept + ": "
									+ outcome.consecutiveSuccesses() + "/"
									+ ConceptConsistencyService.REQUIRED_CONSECUTIVE_SUCCESSES + ".");
				} else if (outcome.counted()) {
					messages.add(
							"Observation streak for " + concept
									+ " reset after an incorrect eligible first attempt.");
				}
			}
		}
		if (messages.size() == base.messages().size()) {
			return base;
		}
		String tone = "warn".equals(base.tone()) ? "warn" : "brief";
		return new ControlFeedbackDto(tone, List.copyOf(messages), List.copyOf(tags));
	}

	private static String friendlyConcept(String tag) {
		return switch (tag) {
			case "http.method.get",
					"http.method.post",
					"http.method.put",
					"http.method.patch",
					"http.method.delete" -> "HTTP method selection";
			case "http.endpoint" -> "resource endpoint selection";
			case "http.path_parameter" -> "path parameter usage";
			case "http.query_parameter" -> "query parameter usage";
			default -> tag;
		};
	}
}
