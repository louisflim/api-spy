package edu.citu.apispy.dto.auth;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequestDto(
		@NotBlank
		@Size(min = 3, max = InputLimits.USERNAME)
		@Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "must contain only letters, numbers, dots, underscores, or hyphens")
		String username,
		@NotBlank @Email @Size(max = InputLimits.EMAIL) String email,
		@NotBlank @Size(min = 8, max = InputLimits.PASSWORD) String password,
		@NotBlank @Size(min = 8, max = InputLimits.PASSWORD) String confirmPassword
) {
	public RegisterRequestDto {
		username = InputSanitizer.id(username);
		email = InputSanitizer.text(email);
		if (email != null) {
			email = email.toLowerCase();
		}
		password = InputSanitizer.password(password);
		confirmPassword = InputSanitizer.password(confirmPassword);
	}
}
