package edu.citu.apispy.domain.simulation;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Student-entered simulated HTTP request data.
 * Treated as challenge input, never dispatched as an outbound network call.
 */
public final class ApiRequest {

	private final String method;
	private final String path;
	private final Map<String, String> queryParameters;
	private final Map<String, String> headers;
	private final String body;

	public ApiRequest(String method, String path, Map<String, String> headers, String body) {
		this.method = method == null ? "" : method.trim().toUpperCase();
		ParsedPath parsed = parse(path);
		this.path = parsed.path();
		this.queryParameters = parsed.queryParameters();
		this.headers = headers == null
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(headers));
		this.body = body == null ? "" : body;
	}

	public String getMethod() {
		return method;
	}

	public String getPath() {
		return path;
	}

	public Map<String, String> getQueryParameters() {
		return queryParameters;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public String getBody() {
		return body;
	}

	private static ParsedPath parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return new ParsedPath("/", Map.of());
		}
		String trimmed = raw.trim();
		String pathPart = trimmed;
		String queryPart = "";
		int queryIndex = trimmed.indexOf('?');
		if (queryIndex >= 0) {
			pathPart = trimmed.substring(0, queryIndex);
			queryPart = trimmed.substring(queryIndex + 1);
		}
		return new ParsedPath(ApiContract.normalizePath(pathPart), parseQuery(queryPart));
	}

	private static Map<String, String> parseQuery(String queryPart) {
		if (queryPart == null || queryPart.isBlank()) {
			return Map.of();
		}
		Map<String, String> values = new LinkedHashMap<>();
		for (String pair : queryPart.split("&")) {
			if (pair.isBlank()) {
				continue;
			}
			int eq = pair.indexOf('=');
			if (eq < 0) {
				values.put(decode(pair), "");
			} else {
				values.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
			}
		}
		return Collections.unmodifiableMap(values);
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}

	private record ParsedPath(String path, Map<String, String> queryParameters) {
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ApiRequest that)) {
			return false;
		}
		return Objects.equals(method, that.method)
				&& Objects.equals(path, that.path)
				&& Objects.equals(queryParameters, that.queryParameters)
				&& Objects.equals(headers, that.headers)
				&& Objects.equals(body, that.body);
	}

	@Override
	public int hashCode() {
		return Objects.hash(method, path, queryParameters, headers, body);
	}
}
