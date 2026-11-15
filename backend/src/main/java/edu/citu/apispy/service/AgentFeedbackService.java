package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptDecision;
import edu.citu.apispy.domain.simulation.SimulationMode;
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
								+ "Let's verify that you can apply this concept in another scenario.");
			} else if (!firstEligible) {
				messages.add(
						"Mission access granted. Retry recorded — prior first-attempt evidence is preserved.");
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
			if (mode == SimulationMode.PRACTICE || mode == SimulationMode.TRAINING) {
				messages.add(
						"Training note: compare method, resource path, and required parameters with the drill documentation. "
								+ "Exact live-mission answers are never revealed here.");
			}
		}

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
