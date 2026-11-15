package edu.citu.apispy.service;

import edu.citu.apispy.content.Module0TopicFile;
import edu.citu.apispy.domain.learning.Module0ProgressEntity;
import edu.citu.apispy.dto.Module0ProgressDto;
import edu.citu.apispy.dto.Module0ProgressUpdateDto;
import edu.citu.apispy.repository.Module0ProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class Module0ProgressService {

	private final Module0ProgressRepository repository;
	private final Module0ContentService contentService;
	private final LearningJsonCodec codec;

	public Module0ProgressService(
			Module0ProgressRepository repository,
			Module0ContentService contentService,
			LearningJsonCodec codec) {
		this.repository = repository;
		this.contentService = contentService;
		this.codec = codec;
	}

	@Transactional(readOnly = true)
	public Module0ProgressDto getForPlayer(String participantCode) {
		return repository.findByParticipantCode(participantCode)
				.map(this::toDto)
				.orElseGet(() -> emptyProgress(participantCode));
	}

	@Transactional
	public Module0ProgressDto updateForPlayer(String participantCode, Module0ProgressUpdateDto update) {
		Module0TopicFile.TopicDefinition topic = contentService.requireTopic(update.topicId());
		Module0ProgressEntity entity = repository.findByParticipantCode(participantCode)
				.orElseGet(() -> new Module0ProgressEntity(
						UUID.randomUUID(),
						participantCode,
						topic.id(),
						codec.writeList(List.of()),
						codec.writeListMap(Map.of()),
						false,
						Instant.now()));

		entity.setLastTopicId(update.lastTopicId());

		Map<String, List<String>> steps = new LinkedHashMap<>(readSteps(entity.getCompletedStepsJson()));
		if (update.stepId() != null && !update.stepId().isBlank()) {
			Set<String> topicSteps = new LinkedHashSet<>(steps.getOrDefault(topic.id(), List.of()));
			topicSteps.add(update.stepId());
			steps.put(topic.id(), List.copyOf(topicSteps));
			entity.setCompletedStepsJson(writeSteps(steps));
		}

		List<String> completedTopics = new ArrayList<>(codec.readList(entity.getCompletedTopicIdsJson()));
		boolean markComplete = Boolean.TRUE.equals(update.markTopicComplete());
		if (markComplete) {
			// Server-side gate: topic completion requires the authored interaction step.
			List<String> topicSteps = steps.getOrDefault(topic.id(), List.of());
			if (!topicSteps.contains("interaction")) {
				throw new IllegalArgumentException(
						"Topic cannot be marked complete until its learning interaction is finished.");
			}
			if (!completedTopics.contains(topic.id())) {
				completedTopics.add(topic.id());
			}
			entity.setCompletedTopicIdsJson(codec.writeList(completedTopics));
		}

		boolean orientationDone = contentService.allTopicIds().stream().allMatch(completedTopics::contains);
		entity.setOrientationCompleted(orientationDone);
		entity.setUpdatedAt(Instant.now());
		repository.save(entity);
		return toDto(entity);
	}

	private Module0ProgressDto emptyProgress(String participantCode) {
		String first = contentService.allTopicIds().stream().findFirst().orElse("welcome");
		return new Module0ProgressDto(
				participantCode,
				first,
				List.of(),
				Map.of(),
				false,
				Instant.EPOCH.toString());
	}

	private Module0ProgressDto toDto(Module0ProgressEntity entity) {
		return new Module0ProgressDto(
				entity.getParticipantCode(),
				entity.getLastTopicId(),
				codec.readList(entity.getCompletedTopicIdsJson()),
				readSteps(entity.getCompletedStepsJson()),
				entity.isOrientationCompleted(),
				entity.getUpdatedAt().toString());
	}

	private Map<String, List<String>> readSteps(String json) {
		return codec.readListMap(json == null || json.isBlank() ? "{}" : json);
	}

	private String writeSteps(Map<String, List<String>> steps) {
		return codec.writeListMap(steps);
	}
}
