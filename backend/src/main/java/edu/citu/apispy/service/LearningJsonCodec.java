package edu.citu.apispy.service;

import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared JSON encode/decode for learning persistence columns. */
@Component
public class LearningJsonCodec {

	private final ObjectMapper objectMapper;

	public LearningJsonCodec(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public String writeList(List<String> values) {
		try {
			return objectMapper.writeValueAsString(values == null ? List.of() : values);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to encode list JSON", ex);
		}
	}

	public List<String> readList(String json) {
		try {
			List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {
			});
			return values == null ? List.of() : List.copyOf(values);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to decode list JSON", ex);
		}
	}

	public String writeStringMap(Map<String, String> values) {
		try {
			return objectMapper.writeValueAsString(values == null ? Map.of() : values);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to encode string map JSON", ex);
		}
	}

	public Map<String, String> readStringMap(String json) {
		try {
			Map<String, String> values = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
			});
			return values == null ? Map.of() : Map.copyOf(values);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to decode string map JSON", ex);
		}
	}

	public String writeBooleanMap(Map<String, Boolean> values) {
		try {
			return objectMapper.writeValueAsString(values == null ? Map.of() : values);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to encode boolean map JSON", ex);
		}
	}

	public Map<String, Boolean> readBooleanMap(String json) {
		try {
			Map<String, Boolean> values = objectMapper.readValue(json, new TypeReference<Map<String, Boolean>>() {
			});
			return values == null ? Map.of() : new LinkedHashMap<>(values);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to decode boolean map JSON", ex);
		}
	}

	public String writeListMap(Map<String, List<String>> values) {
		try {
			return objectMapper.writeValueAsString(values == null ? Map.of() : values);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to encode list-map JSON", ex);
		}
	}

	public Map<String, List<String>> readListMap(String json) {
		try {
			Map<String, List<String>> values =
					objectMapper.readValue(json, new TypeReference<Map<String, List<String>>>() {
					});
			if (values == null) {
				return Map.of();
			}
			Map<String, List<String>> copy = new LinkedHashMap<>();
			values.forEach((key, list) -> copy.put(key, list == null ? List.of() : List.copyOf(list)));
			return Map.copyOf(copy);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to decode list-map JSON", ex);
		}
	}
}
