package edu.citu.apispy.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trims and strips dangerous control characters from user-supplied strings.
 * Passwords keep printable content (including spaces) and only drop null bytes.
 */
public final class InputSanitizer {

	private InputSanitizer() {
	}

	/** Trim + drop C0/C1 controls (except tab). */
	public static String text(String value) {
		if (value == null) {
			return null;
		}
		return stripControls(value, false).trim();
	}

	/** Identifier-like fields (missionId, lessonId, …). */
	public static String id(String value) {
		return text(value);
	}

	/** Optional text: blank after sanitize becomes {@code null}. */
	public static String optionalText(String value) {
		String cleaned = text(value);
		return cleaned == null || cleaned.isEmpty() ? null : cleaned;
	}

	/** Simulate / free-form body: keep newlines and tabs; drop other controls. */
	public static String body(String value) {
		if (value == null) {
			return null;
		}
		return stripControls(value, true);
	}

	/** Passwords: never trim; only strip null bytes so strength rules stay intact. */
	public static String password(String value) {
		if (value == null) {
			return null;
		}
		return value.replace("\u0000", "");
	}

	public static Map<String, String> headers(Map<String, String> headers) {
		if (headers == null || headers.isEmpty()) {
			return Map.of();
		}
		if (headers.size() > InputLimits.HEADER_ENTRIES) {
			throw new IllegalArgumentException(
					"Too many headers (max " + InputLimits.HEADER_ENTRIES + ").");
		}
		Map<String, String> cleaned = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			String name = text(entry.getKey());
			String value = entry.getValue() == null ? "" : stripControls(entry.getValue(), false);
			if (name == null || name.isEmpty()) {
				throw new IllegalArgumentException("Header names must be non-blank.");
			}
			if (name.length() > InputLimits.HEADER_NAME) {
				throw new IllegalArgumentException("Header name exceeds max length.");
			}
			if (value.length() > InputLimits.HEADER_VALUE) {
				throw new IllegalArgumentException("Header value exceeds max length.");
			}
			cleaned.put(name, value);
		}
		return Map.copyOf(cleaned);
	}

	private static String stripControls(String value, boolean keepNewlines) {
		StringBuilder out = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '\u0000') {
				continue;
			}
			if (c == '\t') {
				out.append(c);
				continue;
			}
			if (keepNewlines && (c == '\n' || c == '\r')) {
				out.append(c);
				continue;
			}
			if (Character.isISOControl(c)) {
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}
}
