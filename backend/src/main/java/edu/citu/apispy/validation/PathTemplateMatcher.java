package edu.citu.apispy.validation;

import edu.citu.apispy.domain.simulation.ApiContract;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches authored path templates such as /api/locks/{lockId}.
 */
public final class PathTemplateMatcher {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

	private PathTemplateMatcher() {
	}

	public static Pattern toPattern(String template) {
		String normalized = ApiContract.normalizePath(template);
		Matcher matcher = PLACEHOLDER.matcher(normalized);
		StringBuffer buffer = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(buffer, Matcher.quoteReplacement("([^/]+)"));
		}
		matcher.appendTail(buffer);
		return Pattern.compile("^" + buffer + "$");
	}

	public static Map<String, String> extract(String template, String actualPath) {
		String normalizedTemplate = ApiContract.normalizePath(template);
		String normalizedPath = ApiContract.normalizePath(actualPath);
		Matcher nameMatcher = PLACEHOLDER.matcher(normalizedTemplate);
		Pattern pattern = toPattern(normalizedTemplate);
		Matcher valueMatcher = pattern.matcher(normalizedPath);
		Map<String, String> values = new LinkedHashMap<>();
		if (!valueMatcher.matches()) {
			return values;
		}
		int group = 1;
		while (nameMatcher.find()) {
			values.put(nameMatcher.group(1), valueMatcher.group(group++));
		}
		return values;
	}
}
