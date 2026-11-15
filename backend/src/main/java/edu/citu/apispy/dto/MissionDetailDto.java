package edu.citu.apispy.dto;

import java.util.List;

/**
 * Player-facing mission briefing. Graded answer keys (expected method, exact path,
 * parameter values) stay on server-side {@code ApiContract} / challenge definitions.
 */
public record MissionDetailDto(
		MissionSummaryDto mission,
		ChallengeSummaryDto challenge,
		List<ChallengeSummaryDto> conceptVariants,
		List<ConceptReviewItemDto> conceptReviewQueue
) {
	public MissionDetailDto(
			MissionSummaryDto mission,
			ChallengeSummaryDto challenge,
			List<ChallengeSummaryDto> conceptVariants) {
		this(mission, challenge, conceptVariants, List.of());
	}

	/** Public challenge brief — documentation only; no private validation answers. */
	public record ChallengeSummaryDto(
			String challengeId,
			String variantId,
			String deviceId,
			List<String> conceptTags,
			String documentation
	) {
	}
}
