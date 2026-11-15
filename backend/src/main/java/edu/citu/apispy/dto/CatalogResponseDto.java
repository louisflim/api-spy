package edu.citu.apispy.dto;

import java.util.List;

public record CatalogResponseDto(
		int totalMissions,
		long implementedMissions,
		long plannedMissions,
		List<MissionSummaryDto> missions
) {
}
