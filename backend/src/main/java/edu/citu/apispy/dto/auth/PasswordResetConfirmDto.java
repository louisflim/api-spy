package edu.citu.apispy.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmDto(
		@NotBlank String token,
		@NotBlank @Size(min = 8, max = 128) String password,
		@NotBlank @Size(min = 8, max = 128) String confirmPassword
) {
}
