package edu.citu.apispy.controller;

import edu.citu.apispy.domain.mission.MissionDefinition;
import edu.citu.apispy.domain.mission.Rank;
import edu.citu.apispy.domain.simulation.ApiContract;
import edu.citu.apispy.dto.CatalogResponseDto;
import edu.citu.apispy.dto.MissionDetailDto;
import edu.citu.apispy.dto.MissionSummaryDto;
import edu.citu.apispy.security.AuthenticatedPlayer;
import edu.citu.apispy.dto.MissionProgressDto;
import edu.citu.apispy.service.ConceptReviewService;
import edu.citu.apispy.service.ContractCatalogService;
import edu.citu.apispy.service.MissionCatalogService;
import edu.citu.apispy.service.MissionProgressService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api")
public class CatalogController {

	private final MissionCatalogService missionCatalogService;
	private final ContractCatalogService contractCatalogService;
	private final ConceptReviewService conceptReviewService;
	private final MissionProgressService missionProgressService;

	public CatalogController(
			MissionCatalogService missionCatalogService,
			ContractCatalogService contractCatalogService,
			ConceptReviewService conceptReviewService,
			MissionProgressService missionProgressService) {
		this.missionCatalogService = missionCatalogService;
		this.contractCatalogService = contractCatalogService;
		this.conceptReviewService = conceptReviewService;
		this.missionProgressService = missionProgressService;
	}

	@GetMapping("/catalog")
	public CatalogResponseDto catalog() {
		List<MissionSummaryDto> missions = missionCatalogService.allMissions().stream()
				.map(CatalogController::toSummary)
				.toList();
		long implemented = missionCatalogService.implementedCount();
		return new CatalogResponseDto(
				missionCatalogService.totalConfigured(),
				implemented,
				missionCatalogService.totalConfigured() - implemented,
				missions);
	}

	@GetMapping("/missions/{module}/{rank}/{level}")
	public MissionDetailDto missionDetail(
			@PathVariable int module,
			@PathVariable String rank,
			@PathVariable int level) {
		Rank parsedRank = Rank.valueOf(rank.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
		String missionId = MissionCatalogService.missionId(module, parsedRank, level);
		MissionDefinition mission = missionCatalogService.requirePlayable(missionId);
		String playerId = AuthenticatedPlayer.requireUserIdString();
		MissionProgressDto progress = missionProgressService.getOrCreate(playerId, missionId);
		String selectedPrimaryId = progress.mandatoryDevices() != null && !progress.mandatoryDevices().isEmpty()
				? progress.mandatoryDevices().getFirst().challengeId()
				: mission.getPrimaryChallengeId();
		ApiContract primary = contractCatalogService.requireByChallengeId(selectedPrimaryId);
		List<MissionDetailDto.ChallengeSummaryDto> variants = contractCatalogService
				.findByMissionId(missionId)
				.stream()
				.map(CatalogController::toChallenge)
				.toList();
		return new MissionDetailDto(
				toSummary(mission),
				toChallenge(primary),
				variants,
				conceptReviewService.pendingForParticipant(playerId));
	}

	private static MissionSummaryDto toSummary(MissionDefinition mission) {
		return new MissionSummaryDto(
				mission.getMissionId(),
				mission.getModule(),
				mission.getRank().name(),
				mission.getLevel(),
				mission.getTitle(),
				mission.getStatus().name(),
				mission.isPlayable(),
				mission.getPrimaryChallengeId(),
				mission.getRequiredLessonIds(),
				mission.getConceptTags(),
				mission.getScenarioFocus());
	}

	/** Public brief only — graded method/path/param answers stay on ApiContract. */
	private static MissionDetailDto.ChallengeSummaryDto toChallenge(ApiContract contract) {
		return new MissionDetailDto.ChallengeSummaryDto(
				contract.getChallengeId(),
				contract.getVariantId(),
				contract.getDeviceId(),
				contract.getConceptTags(),
				contract.getDocumentation());
	}
}
