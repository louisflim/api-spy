package edu.citu.apispy.service;

import edu.citu.apispy.content.ContentLoader;
import edu.citu.apispy.content.Module0TopicFile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class Module0ContentService {

	private final Module0TopicFile file;

	public Module0ContentService(ContentLoader contentLoader) {
		this.file = contentLoader.load("content/module0/topics.json", Module0TopicFile.class);
	}

	public String title() {
		return file.title();
	}

	public List<Module0TopicFile.TopicDefinition> topics() {
		return file.topics();
	}

	public Module0TopicFile.TopicDefinition requireTopic(String topicId) {
		return file.topics().stream()
				.filter(topic -> topic.id().equals(topicId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown Module 0 topic: " + topicId));
	}

	public List<String> allTopicIds() {
		return file.topics().stream().map(Module0TopicFile.TopicDefinition::id).toList();
	}
}
