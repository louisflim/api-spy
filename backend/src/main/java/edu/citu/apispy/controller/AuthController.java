package edu.citu.apispy.controller;

import edu.citu.apispy.auth.AuthRateLimiter;
import edu.citu.apispy.dto.auth.AuthUserResponseDto;
import edu.citu.apispy.dto.auth.LoginRequestDto;
import edu.citu.apispy.dto.auth.PasswordResetConfirmDto;
import edu.citu.apispy.dto.auth.PasswordResetRequestDto;
import edu.citu.apispy.dto.auth.RegisterRequestDto;
import edu.citu.apispy.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final AuthRateLimiter authRateLimiter;

	public AuthController(AuthService authService, AuthRateLimiter authRateLimiter) {
		this.authService = authService;
		this.authRateLimiter = authRateLimiter;
	}

	@GetMapping("/csrf")
	public Map<String, String> csrf(CsrfToken csrfToken) {
		return Map.of(
				"headerName", csrfToken.getHeaderName(),
				"parameterName", csrfToken.getParameterName(),
				"token", csrfToken.getToken());
	}

	@PostMapping("/register")
	public ResponseEntity<AuthUserResponseDto> register(
			@Valid @RequestBody RegisterRequestDto request,
			HttpServletRequest httpRequest) {
		authRateLimiter.check("register:" + clientKey(httpRequest));
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request, httpRequest));
	}

	@PostMapping("/login")
	public AuthUserResponseDto login(
			@Valid @RequestBody LoginRequestDto request,
			HttpServletRequest httpRequest) {
		authRateLimiter.check("login:" + clientKey(httpRequest));
		return authService.login(request, httpRequest);
	}

	@PostMapping("/logout")
	public Map<String, String> logout(HttpServletRequest request) {
		authService.logout(request);
		return Map.of("message", "Logged out.");
	}

	@GetMapping("/me")
	public AuthUserResponseDto me() {
		return authService.currentUser()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required."));
	}

	@GetMapping("/password-reset/status")
	public Map<String, Object> passwordResetStatus() {
		return authService.passwordResetStatus();
	}

	@PostMapping("/password-reset/request")
	public Map<String, Object> requestPasswordReset(
			@Valid @RequestBody PasswordResetRequestDto request,
			HttpServletRequest httpRequest) {
		authRateLimiter.check("reset:" + clientKey(httpRequest));
		return authService.requestPasswordReset(request);
	}

	@PostMapping("/password-reset/confirm")
	public Map<String, Object> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmDto request) {
		return authService.confirmPasswordReset(request);
	}

	private static String clientKey(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
	}
}
