package edu.citu.apispy.dto.auth;

import edu.citu.apispy.web.InputLimits;
import edu.citu.apispy.web.InputSanitizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDto(
		@NotBlank @Email @Size(max = InputLimits.EMAIL) String email,
		@NotBlank @Size(max = InputLimits.PASSWORD) String password
) {
	public LoginRequestDto {
		email = InputSanitizer.text(email);
		if (email != null) {
			email = email.toLowerCase();
		}
		password = InputSanitizer.password(password);
	}
}
