package edu.citu.apispy.service;

import java.util.List;

/**
 * Authored mapping from Module 1 concept tags to mini-lessons and ungraded practice variants.
 * Deterministic — no AI generation.
 */
public final class ConceptSupportCatalog {

	private ConceptSupportCatalog() {
	}

	public static String reviewLessonId(String conceptTag) {
		return switch (conceptTag) {
			case "rest.fundamentals" -> "rest-fundamentals";
			case "http.method.get" -> "http-get";
			case "http.endpoint" -> "endpoints";
			case "http.path_parameter" -> "path-parameters";
			case "http.query_parameter" -> "query-parameters";
			case "http.response" -> "simulated-responses";
			case "http.method.post" -> "http-post";
			case "http.method.put", "http.method.patch" -> "http-put-patch";
			case "http.method.delete" -> "http-delete";
			default -> null;
		};
	}

	/**
	 * Ordered authored practice drills for a concept. First entry is preferred for the
	 * adaptive door; later entries support voluntary further practice rounds.
	 */
	public static List<String> practiceChallengeIds(String conceptTag) {
		return switch (conceptTag) {
			case "http.method.get", "http.endpoint", "rest.fundamentals" ->
					List.of("chal-practice-m1-camera", "chal-practice-m1-l1");
			case "http.path_parameter" ->
					List.of("chal-practice-m1-panel", "chal-practice-m1-l2");
			case "http.query_parameter" ->
					List.of("chal-practice-m1-badge-query", "chal-practice-m1-l3");
			case "http.method.post" ->
					List.of("chal-practice-m1-courier", "chal-practice-m1-l4");
			case "http.method.put", "http.method.patch" ->
					List.of("chal-practice-m1-roster", "chal-practice-m1-l5");
			case "http.method.delete" ->
					List.of("chal-practice-m1-token", "chal-practice-m1-l6");
			default -> List.of();
		};
	}

	public static String friendlyConcept(String conceptTag) {
		return switch (conceptTag) {
			case "http.method.get" -> "GET requests";
			case "http.method.post" -> "POST requests";
			case "http.method.put" -> "PUT requests";
			case "http.method.patch" -> "PATCH requests";
			case "http.method.delete" -> "DELETE requests";
			case "http.endpoint" -> "API endpoints";
			case "http.path_parameter" -> "path parameters";
			case "http.query_parameter" -> "query parameters";
			case "rest.fundamentals" -> "REST fundamentals";
			case "http.response" -> "HTTP responses";
			default -> conceptTag;
		};
	}
}
