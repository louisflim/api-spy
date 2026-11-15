package edu.citu.apispy.domain.simulation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fictional HTTP response returned to the learner UI.
 */
public final class SimulatedHttpResponse {

	private final int status;
	private final Map<String, String> headers;
	private final Object body;

	public SimulatedHttpResponse(int status, Map<String, String> headers, Object body) {
		this.status = status;
		this.headers = headers == null
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(headers));
		this.body = body;
	}

	public int getStatus() {
		return status;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public Object getBody() {
		return body;
	}

	public static SimulatedHttpResponse success(ApiContract contract) {
		return new SimulatedHttpResponse(
				contract.getSuccessStatus(),
				Map.of("Content-Type", "application/json"),
				contract.getSuccessBody());
	}

	public static SimulatedHttpResponse error(int status, String message, Map<String, Object> details) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", message);
		if (details != null) {
			body.putAll(details);
		}
		return new SimulatedHttpResponse(
				status,
				Map.of("Content-Type", "application/json"),
				body);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof SimulatedHttpResponse that)) {
			return false;
		}
		return status == that.status
				&& Objects.equals(headers, that.headers)
				&& Objects.equals(body, that.body);
	}

	@Override
	public int hashCode() {
		return Objects.hash(status, headers, body);
	}
}
