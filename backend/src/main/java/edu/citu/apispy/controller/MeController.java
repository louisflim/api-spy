package edu.citu.apispy.controller;

import edu.citu.apispy.domain.learning.FirstAttemptRecord;
import edu.citu.apispy.dto.ConceptProgressDto;
import edu.citu.apispy.dto.FirstAttemptRecordDto;
import edu.citu.apispy.dto.auth.AuthUserResponseDto;
import edu.citu.apispy.security.AuthenticatedPlayer;
import edu.citu.apispy.service.AuthService;
import edu.citu.apispy.service.ConceptProgressService;
import edu.citu.apispy.service.FirstAttemptService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/me")
public class MeController {

	private final AuthService authService;
	private final ConceptProgressService conceptProgressService;
	private final FirstAttemptService firstAttemptService;

	public MeController(
			AuthService authService,
			ConceptProgressService conceptProgressService,
			FirstAttemptService firstAttemptService) {
		this.authService = authService;
		this.conceptProgressService = conceptProgressService;
		this.firstAttemptService = firstAttemptService;
	}

	@GetMapping
	public AuthUserResponseDto me() {
		return authService.currentUser()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required."));
	}

	@GetMapping("/concepts")
	public List<ConceptProgressDto> concepts() {
		String playerId = AuthenticatedPlayer.requireUserIdString();
		return conceptProgressService.allForParticipant(playerId).values().stream()
				.map(view -> new ConceptProgressDto(
						view.conceptId(),
						view.stage().name(),
						view.updatedAt().toString()))
				.toList();
	}

	@GetMapping("/first-attempts")
	public List<FirstAttemptRecordDto> firstAttempts() {
		String playerId = AuthenticatedPlayer.requireUserIdString();
		return firstAttemptService.findAllForParticipant(playerId).stream()
				.map(MeController::toDto)
				.toList();
	}

	private static FirstAttemptRecordDto toDto(FirstAttemptRecord record) {
		return new FirstAttemptRecordDto(
				record.getParticipantCode(),
				record.getMissionId(),
				record.getDeviceId(),
				record.getChallengeId(),
				record.getVariantId(),
				record.getConceptTags(),
				record.getMethod(),
				record.getPath(),
				record.getHeaders(),
				record.getBody(),
				record.getErrorCodes(),
				record.isFirstAttemptCorrect(),
				record.getConceptFirstAttemptCorrect(),
				record.getSubmittedAt().toString());
	}
}
