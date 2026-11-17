package edu.citu.apispy.controller;

import edu.citu.apispy.dto.ConceptReviewItemDto;
import edu.citu.apispy.dto.PracticeOutcomeDto;
import edu.citu.apispy.dto.PracticeOutcomeRequestDto;
import edu.citu.apispy.security.AuthenticatedPlayer;
import edu.citu.apispy.service.ConceptReviewService;
import edu.citu.apispy.service.PracticeOutcomeService;
import edu.citu.apispy.web.InputLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Learning support APIs that remain after physical adaptive practice doors were retired.
 * Optional field doors / mini-lesson recovery endpoints are gone; practice-outcome and
 * later-review queue stay for Module 0 / mission training research telemetry.
 */
@RestController
@Validated
@RequestMapping("/api/learning/support")
public class LearningSupportController {

	private final PracticeOutcomeService practiceOutcomeService;
	private final ConceptReviewService conceptReviewService;

	public LearningSupportController(
			PracticeOutcomeService practiceOutcomeService,
			ConceptReviewService conceptReviewService) {
		this.practiceOutcomeService = practiceOutcomeService;
		this.conceptReviewService = conceptReviewService;
	}

	@GetMapping("/{roomMissionId}")
	public Map<String, Object> recovery(
			@PathVariable @Size(max = InputLimits.ID) String roomMissionId) {
		String playerId = AuthenticatedPlayer.requireUserIdString();
		List<ConceptReviewItemDto> laterReview = conceptReviewService.pendingForParticipant(playerId);
		List<PracticeOutcomeDto> outcomes = practiceOutcomeService.forParticipant(playerId);
		return Map.of(
				"roomMissionId", roomMissionId,
				"supportOffers", List.of(),
				"adaptivePracticeDoors", List.of(),
				"pendingLaterReview", laterReview,
				"practiceOutcomes", outcomes);
	}

	@PostMapping("/practice-outcome")
	public PracticeOutcomeDto recordOutcome(@Valid @RequestBody PracticeOutcomeRequestDto body) {
		String playerId = AuthenticatedPlayer.requireUserIdString();
		return practiceOutcomeService.record(
				playerId,
				body.conceptTag(),
				body.challengeId(),
				body.roomMissionId(),
				body.correct());
	}
}
