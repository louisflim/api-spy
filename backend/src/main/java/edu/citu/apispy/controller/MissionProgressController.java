package edu.citu.apispy.controller;

import edu.citu.apispy.domain.mission.RoomResetReason;
import edu.citu.apispy.dto.AcknowledgeLessonReviewDto;
import edu.citu.apispy.dto.BossDefeatRequestDto;
import edu.citu.apispy.dto.CombatDeathRequestDto;
import edu.citu.apispy.dto.MissionCompleteRequestDto;
import edu.citu.apispy.dto.MissionProgressDto;
import edu.citu.apispy.dto.RoomResetRequestDto;
import edu.citu.apispy.dto.TerminalVerificationDto;
import edu.citu.apispy.security.AuthenticatedPlayer;
import edu.citu.apispy.service.MissionProgressService;
import edu.citu.apispy.service.RequestSimulationService;
import edu.citu.apispy.web.InputLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@Validated
@RequestMapping("/api/missions")
public class MissionProgressController {

	private final MissionProgressService missionProgressService;
	private final RequestSimulationService requestSimulationService;

	public MissionProgressController(
			MissionProgressService missionProgressService,
			RequestSimulationService requestSimulationService) {
		this.missionProgressService = missionProgressService;
		this.requestSimulationService = requestSimulationService;
	}

	@GetMapping("/progress")
	public List<MissionProgressDto> allProgress() {
		return missionProgressService.listForParticipant(AuthenticatedPlayer.requireUserIdString());
	}

	@GetMapping("/{missionId}/progress")
	public MissionProgressDto progress(@PathVariable @Size(max = InputLimits.ID) String missionId) {
		return missionProgressService.getOrCreate(AuthenticatedPlayer.requireUserIdString(), missionId);
	}

	/** Read-only Terminal Verification Progress for a graded mission terminal. */
	@GetMapping("/{missionId}/terminal-verification")
	public TerminalVerificationDto terminalVerification(
			@PathVariable @Size(max = InputLimits.ID) String missionId) {
		return requestSimulationService.terminalVerificationSnapshot(
				AuthenticatedPlayer.requireUserIdString(),
				missionId);
	}

	@PostMapping("/{missionId}/reset")
	public ResponseEntity<MissionProgressDto> reset(
			@PathVariable @Size(max = InputLimits.ID) String missionId,
			@Valid @RequestBody RoomResetRequestDto request) {
		RoomResetReason reason = parseReason(request.reason());
		return ResponseEntity.ok(missionProgressService.resetRoom(
				AuthenticatedPlayer.requireUserIdString(),
				missionId,
				reason,
				request.roomGeneration()));
	}

	/**
	 * Clears the SRS v1.3 forced ungraded-lesson gate after the player revisits the
	 * required lesson. Does not reveal graded challenge solutions.
	 */
	@PostMapping("/{missionId}/acknowledge-lesson-review")
	public ResponseEntity<MissionProgressDto> acknowledgeLessonReview(
			@PathVariable @Size(max = InputLimits.ID) String missionId,
			@Valid @RequestBody AcknowledgeLessonReviewDto request) {
		return ResponseEntity.ok(missionProgressService.acknowledgeLessonReview(
				AuthenticatedPlayer.requireUserIdString(),
				missionId,
				request.lessonId()));
	}

	/** Starts a server-authorized temporary replay run for a career-cleared mission. */
	@PostMapping("/{missionId}/replay")
	public ResponseEntity<MissionProgressDto> replay(
			@PathVariable @Size(max = InputLimits.ID) String missionId) {
		return ResponseEntity.ok(missionProgressService.startReplay(
				AuthenticatedPlayer.requireUserIdString(),
				missionId));
	}

	/**
	 * Binds a combat-death report to the active room generation and authorizes COMBAT reset.
	 * Does not accept client health as proof of death.
	 */
	@PostMapping("/{missionId}/combat-death")
	public ResponseEntity<MissionProgressDto> combatDeath(
			@PathVariable @Size(max = InputLimits.ID) String missionId,
			@Valid @RequestBody CombatDeathRequestDto request) {
		return ResponseEntity.ok(missionProgressService.reportCombatDeath(
				AuthenticatedPlayer.requireUserIdString(),
				missionId,
				request == null ? null : request.roomGeneration()));
	}

	/**
	 * Level 7: records the boss defeat for the active room generation. Rejected (400)
	 * while the boss shield is up, i.e. until every mandatory API device is activated.
	 */
	@PostMapping("/{missionId}/boss-defeated")
	public ResponseEntity<MissionProgressDto> bossDefeated(
			@PathVariable @Size(max = InputLimits.ID) String missionId,
			@Valid @RequestBody BossDefeatRequestDto request) {
		return ResponseEntity.ok(missionProgressService.reportBossDefeated(
				AuthenticatedPlayer.requireUserIdString(),
				missionId,
				request == null ? null : request.roomGeneration()));
	}

	@PostMapping("/{missionId}/complete")
	public ResponseEntity<MissionProgressDto> complete(
			@PathVariable @Size(max = InputLimits.ID) String missionId,
			@Valid @RequestBody(required = false) MissionCompleteRequestDto request) {
		String roomGeneration = request == null ? null : request.roomGeneration();
		return ResponseEntity.ok(missionProgressService.completeAfterExit(
				AuthenticatedPlayer.requireUserIdString(),
				missionId,
				roomGeneration));
	}

	private static RoomResetReason parseReason(String raw) {
		try {
			return RoomResetReason.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (Exception ex) {
			throw new IllegalArgumentException("reason must be API_STALLED, COMBAT, or PLAYER");
		}
	}
}
