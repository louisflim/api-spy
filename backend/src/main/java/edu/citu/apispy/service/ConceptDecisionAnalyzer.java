package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptDecision;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.domain.simulation.ErrorCode;
import edu.citu.apispy.validation.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps multi-error validation results into per-concept decisions.
 * Does not weaken blocking contract validation.
 */
@Service
public class ConceptDecisionAnalyzer {

	public List<ConceptDecision> analyze(ApiContract contract, ValidationResult result) {
		List<ConceptDecision> decisions = new ArrayList<>();
		for (String conceptTag : contract.getConceptTags()) {
			boolean applicable = isApplicable(conceptTag, contract);
			boolean correct = applicable && FirstAttemptService.correctForConcept(conceptTag, result, contract);
			List<String> relatedErrors = relatedErrorNames(conceptTag, result);
			decisions.add(new ConceptDecision(conceptTag, applicable, correct, relatedErrors));
		}
		return List.copyOf(decisions);
	}

	/** Blocking errors prevent mandatory device unlock (existing validator semantics). */
	public boolean hasBlockingContractError(ValidationResult result) {
		return !result.isCorrect();
	}

	private static boolean isApplicable(String conceptTag, ApiContract contract) {
		return switch (conceptTag) {
			case "http.path_parameter" -> !contract.getPathParameters().isEmpty()
					|| contract.getPathTemplate().contains("{");
			case "http.query_parameter" -> !contract.getQueryParameters().isEmpty();
			case "http.response" -> false;
			default -> contract.getConceptTags().contains(conceptTag);
		};
	}

	private static List<String> relatedErrorNames(String conceptTag, ValidationResult result) {
		Set<ErrorCode> errorCodes = result.getErrorCodes();
		EnumSet<ErrorCode> related = EnumSet.noneOf(ErrorCode.class);
		switch (conceptTag) {
			case "http.method.get",
					"http.method.post",
					"http.method.put",
					"http.method.patch",
					"http.method.delete" -> {
				if (errorCodes.contains(ErrorCode.METHOD)) {
					related.add(ErrorCode.METHOD);
				}
			}
			case "http.endpoint" -> {
				if (errorCodes.contains(ErrorCode.ENDPOINT)) {
					related.add(ErrorCode.ENDPOINT);
				}
			}
			case "http.path_parameter" -> {
				for (ErrorCode code : errorCodes) {
					if (code == ErrorCode.ENDPOINT
							|| (result.pathParameterFailed() && ValidationResult.isParameterCode(code))) {
						related.add(code);
					}
				}
			}
			case "http.query_parameter" -> {
				for (ErrorCode code : errorCodes) {
					if (result.queryParameterFailed() && ValidationResult.isParameterCode(code)) {
						related.add(code);
					}
				}
			}
			case "rest.fundamentals" -> {
				if (errorCodes.contains(ErrorCode.METHOD)) {
					related.add(ErrorCode.METHOD);
				}
				if (errorCodes.contains(ErrorCode.ENDPOINT)) {
					related.add(ErrorCode.ENDPOINT);
				}
			}
			default -> {
			}
		}
		return related.stream().map(Enum::name).sorted().collect(Collectors.toList());
	}
}
