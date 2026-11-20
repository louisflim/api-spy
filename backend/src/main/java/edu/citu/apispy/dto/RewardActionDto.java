package edu.citu.apispy.dto;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.Size;

/** Body for gun upgrades ({@code stat}) and gun purchase/equip ({@code weaponId}). */
public record RewardActionDto(
		@Size(max = InputLimits.ID) String stat,
		@Size(max = InputLimits.ID) String weaponId,
		@Size(max = InputLimits.ID) String characterId
) {
	public RewardActionDto {
		stat = InputSanitizer.optionalText(stat);
		weaponId = InputSanitizer.optionalText(weaponId);
		characterId = InputSanitizer.optionalText(characterId);
	}
}
