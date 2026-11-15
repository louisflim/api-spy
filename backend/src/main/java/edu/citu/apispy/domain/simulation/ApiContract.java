package edu.citu.apispy.domain.simulation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Authored fictional API contract. Student-entered URLs are never executed.
 */
public final class ApiContract {

	private final String challengeId;
	private final String variantId;
	private final String missionId;
	private final String deviceId;
	private final List<String> conceptTags;
	private final String expectedMethod;
	private final String pathTemplate;
	private final Map<String, String> pathParameters;
	private final Map<String, String> queryParameters;
	private final int successStatus;
	private final Map<String, Object> successBody;
	private final String documentation;
	private final String deviceUnlockState;

	public ApiContract(
			String challengeId,
			String variantId,
			String missionId,
			String deviceId,
			List<String> conceptTags,
			String expectedMethod,
			String pathTemplate,
			Map<String, String> pathParameters,
			Map<String, String> queryParameters,
			int successStatus,
			Map<String, Object> successBody,
			String documentation,
			String deviceUnlockState) {
		this.challengeId = Objects.requireNonNull(challengeId, "challengeId");
		this.variantId = Objects.requireNonNull(variantId, "variantId");
		this.missionId = Objects.requireNonNull(missionId, "missionId");
		this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
		this.conceptTags = List.copyOf(Objects.requireNonNull(conceptTags, "conceptTags"));
		this.expectedMethod = Objects.requireNonNull(expectedMethod, "expectedMethod").toUpperCase();
		this.pathTemplate = normalizePath(Objects.requireNonNull(pathTemplate, "pathTemplate"));
		this.pathParameters = immutableCopy(pathParameters);
		this.queryParameters = immutableCopy(queryParameters);
		this.successStatus = successStatus;
		this.successBody = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(successBody)));
		this.documentation = Objects.requireNonNull(documentation, "documentation");
		this.deviceUnlockState = deviceUnlockState == null ? "OPEN" : deviceUnlockState;
	}

	public String getChallengeId() {
		return challengeId;
	}

	public String getVariantId() {
		return variantId;
	}

	public String getMissionId() {
		return missionId;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public List<String> getConceptTags() {
		return conceptTags;
	}

	public String getPrimaryConceptTag() {
		return conceptTags.isEmpty() ? "unspecified" : conceptTags.getFirst();
	}

	public String getExpectedMethod() {
		return expectedMethod;
	}

	public String getPathTemplate() {
		return pathTemplate;
	}

	/** @deprecated Prefer {@link #getPathTemplate()}; kept for Day 1 call sites during migration. */
	@Deprecated
	public String getExpectedPath() {
		return pathTemplate;
	}

	public Map<String, String> getPathParameters() {
		return pathParameters;
	}

	public Map<String, String> getQueryParameters() {
		return queryParameters;
	}

	public int getSuccessStatus() {
		return successStatus;
	}

	public Map<String, Object> getSuccessBody() {
		return successBody;
	}

	public String getDocumentation() {
		return documentation;
	}

	public String getDeviceUnlockState() {
		return deviceUnlockState;
	}

	public String getContractId() {
		return challengeId;
	}

	public static String normalizePath(String path) {
		if (path == null || path.isBlank()) {
			return "/";
		}
		String trimmed = path.trim();
		int queryIndex = trimmed.indexOf('?');
		if (queryIndex >= 0) {
			trimmed = trimmed.substring(0, queryIndex);
		}
		if (!trimmed.startsWith("/")) {
			trimmed = "/" + trimmed;
		}
		if (trimmed.length() > 1 && trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}

	private static Map<String, String> immutableCopy(Map<String, String> source) {
		if (source == null || source.isEmpty()) {
			return Map.of();
		}
		return Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}
}
