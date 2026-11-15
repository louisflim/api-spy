package edu.citu.apispy.controller;

import edu.citu.apispy.content.Module0TopicFile;
import edu.citu.apispy.dto.Module0ProgressDto;
import edu.citu.apispy.dto.Module0ProgressUpdateDto;
import edu.citu.apispy.security.AuthenticatedPlayer;
import edu.citu.apispy.service.Module0ContentService;
import edu.citu.apispy.service.Module0ProgressService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/module0")
public class Module0Controller {

	private final Module0ContentService contentService;
	private final Module0ProgressService progressService;

	public Module0Controller(Module0ContentService contentService, Module0ProgressService progressService) {
		this.contentService = contentService;
		this.progressService = progressService;
	}

	@GetMapping
	public Map<String, Object> academy() {
		List<Map<String, Object>> topics = contentService.topics().stream()
				.map(Module0Controller::toTopicMap)
				.toList();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("moduleId", "module0");
		body.put("title", contentService.title());
		body.put("outsideCatalog", true);
		body.put("graded", false);
		body.put("includedInKpis", false);
		body.put("topics", topics);
		return body;
	}

	@GetMapping("/progress")
	public Module0ProgressDto progress() {
		String playerId = AuthenticatedPlayer.requireUserIdString();
		return progressService.getForPlayer(playerId);
	}

	@PutMapping("/progress")
	public Module0ProgressDto updateProgress(@Valid @RequestBody Module0ProgressUpdateDto update) {
		String playerId = AuthenticatedPlayer.requireUserIdString();
		return progressService.updateForPlayer(playerId, update);
	}

	private static Map<String, Object> toTopicMap(Module0TopicFile.TopicDefinition topic) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", topic.id());
		map.put("order", topic.order());
		map.put("title", topic.title());
		map.put("objective", topic.objective());
		map.put("controlIntro", topic.controlIntro());
		map.put("explanation", topic.explanation());
		map.put("workedExample", topic.workedExample());
		map.put("interaction", topic.interaction());
		map.put("completionMessage", topic.completionMessage());
		return map;
	}
}
