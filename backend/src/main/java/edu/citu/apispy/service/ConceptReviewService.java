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
			boolean already = memoryRows.values().stream().anyMatch(row ->
					row.participantCode().equals(participantCode)
							&& row.conceptTag().equals(conceptTag)
							&& (STATUS_QUEUED.equals(row.status()) || STATUS_SCHEDULED.equals(row.status())));
			if (already) {
				return;
			}
			String key = participantCode + "|" + conceptTag + "|" + sourceMissionId;
			memoryRows.putIfAbsent(
					key,
					new ReviewRow(participantCode, conceptTag, sourceMissionId, STATUS_QUEUED, reviewChallengeId));
			return;
		}
		boolean already = repository.findByParticipantCode(participantCode).stream().anyMatch(entity ->
				entity.getConceptTag().equals(conceptTag)
						&& (STATUS_QUEUED.equals(entity.getStatus()) || STATUS_SCHEDULED.equals(entity.getStatus())));
		if (already) {
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
		List<String> authored = ConceptSupportCatalog.practiceChallengeIds(conceptTag);
		if (!authored.isEmpty()) {
			return authored.getLast();
		}
		try {
			return contractCatalogService.requireByChallengeId("chal-practice-m1-l1").getChallengeId();
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private record ReviewRow(
			String participantCode,
			String conceptTag,
			String sourceMissionId,
			String status,
			String reviewChallengeId) {
	}
}
