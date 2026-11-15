package edu.citu.apispy.content;

import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class ContentLoader {

	private final ObjectMapper objectMapper;

	public ContentLoader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public <T> T load(String classpathLocation, Class<T> type) {
		ClassPathResource resource = new ClassPathResource(classpathLocation);
		try (InputStream inputStream = resource.getInputStream()) {
			return objectMapper.readValue(inputStream, type);
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to load content: " + classpathLocation, ex);
		}
	}
}
