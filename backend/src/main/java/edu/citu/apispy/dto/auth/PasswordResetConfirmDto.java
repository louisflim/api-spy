package edu.citu.apispy.dto.auth;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmDto(
		@NotBlank @Size(max = InputLimits.RESET_TOKEN) String token,
		@NotBlank @Size(min = 8, max = InputLimits.PASSWORD) String password,
		@NotBlank @Size(min = 8, max = InputLimits.PASSWORD) String confirmPassword
) {
	public PasswordResetConfirmDto {
		token = InputSanitizer.id(token);
		password = InputSanitizer.password(password);
		confirmPassword = InputSanitizer.password(confirmPassword);
	}
}
