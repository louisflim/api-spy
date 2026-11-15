package edu.citu.apispy.service;

import edu.citu.apispy.domain.learning.ConceptReviewEntity;
import edu.citu.apispy.dto.ConceptReviewItemDto;
import edu.citu.apispy.repository.ConceptReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schedules later authored review scenarios. Does not claim long-term retention
 * from same-session review.
 */
@Service
public class ConceptReviewService {

	public static final String STATUS_QUEUED = "QUEUED";
	public static final String STATUS_SCHEDULED = "SCHEDULED";

	private final ConceptReviewRepository repository;
	private final ContractCatalogService contractCatalogService;
	private final ConcurrentHashMap<String, ReviewRow> memoryRows;
	private final boolean memoryMode;

	@Autowired
	public ConceptReviewService(
			ConceptReviewRepository repository,
			ContractCatalogService contractCatalogService) {
		this.repository = repository;
		this.contractCatalogService = contractCatalogService;
		this.memoryRows = null;
		this.memoryMode = false;
	}

	private ConceptReviewService(ContractCatalogService contractCatalogService) {
		this.repository = null;
		this.contractCatalogService = contractCatalogService;
		this.memoryRows = new ConcurrentHashMap<>();
		this.memoryMode = true;
	}

	public static ConceptReviewService inMemory(ContractCatalogService contracts) {
		return new ConceptReviewService(contracts);
	}

	public void enqueue(String participantCode, String conceptTag, String sourceMissionId) {
		String reviewChallengeId = suggestReviewChallenge(conceptTag);
		if (memoryMode) {
			String key = participantCode + "|" + conceptTag + "|" + sourceMissionId;
			memoryRows.putIfAbsent(
					key,
					new ReviewRow(participantCode, conceptTag, sourceMissionId, STATUS_QUEUED, reviewChallengeId));
			return;
		}
		Instant now = Instant.now();
		repository.save(new ConceptReviewEntity(
				UUID.randomUUID(),
				participantCode,
				conceptTag,
				sourceMissionId,
				STATUS_QUEUED,
				reviewChallengeId,
				null,
				now,
				now));
	}

	public List<ConceptReviewItemDto> pendingForParticipant(String participantCode) {
		List<ConceptReviewItemDto> items = new ArrayList<>();
		if (memoryMode) {
			for (ReviewRow row : memoryRows.values()) {
				if (!row.participantCode().equals(participantCode)) {
					continue;
				}
				if (STATUS_QUEUED.equals(row.status()) || STATUS_SCHEDULED.equals(row.status())) {
					items.add(new ConceptReviewItemDto(
							row.conceptTag(),
							row.sourceMissionId(),
							row.status(),
							row.reviewChallengeId()));
				}
			}
			return List.copyOf(items);
		}
		for (ConceptReviewEntity entity : repository.findByParticipantCode(participantCode)) {
			if (STATUS_QUEUED.equals(entity.getStatus()) || STATUS_SCHEDULED.equals(entity.getStatus())) {
				items.add(new ConceptReviewItemDto(
						entity.getConceptTag(),
						entity.getSourceMissionId(),
						entity.getStatus(),
						entity.getReviewChallengeId()));
			}
		}
		return List.copyOf(items);
	}

	private String suggestReviewChallenge(String conceptTag) {
		return switch (conceptTag) {
			case "http.method.get", "http.endpoint", "rest.fundamentals" -> "chal-practice-m1-l1";
			case "http.path_parameter" -> "chal-practice-m1-l2";
			case "http.query_parameter" -> "chal-practice-m1-l3";
			case "http.method.post" -> "chal-practice-m1-l4";
			case "http.method.put", "http.method.patch" -> "chal-practice-m1-l5";
			case "http.method.delete" -> "chal-practice-m1-l6";
			default -> {
				try {
					yield contractCatalogService.requireByChallengeId("chal-practice-m1-l1").getChallengeId();
				} catch (IllegalArgumentException ex) {
					yield null;
				}
			}
		};
	}

	private record ReviewRow(
			String participantCode,
			String conceptTag,
			String sourceMissionId,
			String status,
			String reviewChallengeId) {
	}
}
