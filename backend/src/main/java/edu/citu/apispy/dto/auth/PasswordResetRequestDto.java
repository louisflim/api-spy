package edu.citu.apispy.dto.auth;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequestDto(
		@NotBlank @Email @Size(max = InputLimits.EMAIL) String email
) {
	public PasswordResetRequestDto {
		email = InputSanitizer.text(email);
		if (email != null) {
			email = email.toLowerCase();
		}
	}
}
