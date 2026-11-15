package edu.citu.apispy.service;

import edu.citu.apispy.auth.PasswordPolicy;
import edu.citu.apispy.config.AuthProperties;
import edu.citu.apispy.domain.auth.PasswordResetToken;
import edu.citu.apispy.domain.auth.PlayerProfile;
import edu.citu.apispy.domain.auth.UserAccount;
import edu.citu.apispy.dto.auth.AuthUserResponseDto;
import edu.citu.apispy.dto.auth.LoginRequestDto;
import edu.citu.apispy.dto.auth.PasswordResetConfirmDto;
import edu.citu.apispy.dto.auth.PasswordResetRequestDto;
import edu.citu.apispy.dto.auth.RegisterRequestDto;
import edu.citu.apispy.repository.PasswordResetTokenRepository;
import edu.citu.apispy.repository.PlayerProfileRepository;
import edu.citu.apispy.repository.UserAccountRepository;
import edu.citu.apispy.security.AccountPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

	private static final String GENERIC_LOGIN_ERROR = "Invalid email or password.";
	private static final String GENERIC_RESET_MESSAGE =
			"If an account exists for that email and recovery is available, further instructions would be sent.";

	private final UserAccountRepository userAccountRepository;
	private final PlayerProfileRepository playerProfileRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final AuthProperties authProperties;
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthService(
			UserAccountRepository userAccountRepository,
			PlayerProfileRepository playerProfileRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager,
			AuthProperties authProperties) {
		this.userAccountRepository = userAccountRepository;
		this.playerProfileRepository = playerProfileRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.authProperties = authProperties;
	}

	@Transactional
	public AuthUserResponseDto register(RegisterRequestDto request, HttpServletRequest httpRequest) {
		String username = request.username().trim();
		String email = request.email().trim().toLowerCase(Locale.ROOT);

		List<String> passwordErrors = PasswordPolicy.validate(request.password(), request.confirmPassword());
		if (!passwordErrors.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", passwordErrors));
		}
		if (username.length() < 3) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must be at least 3 characters.");
		}
		if (userAccountRepository.existsByUsernameIgnoreCase(username)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken.");
		}
		if (userAccountRepository.existsByEmailIgnoreCase(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.");
		}

		String hash = passwordEncoder.encode(request.password());
		UserAccount account = userAccountRepository.save(new UserAccount(username, email, hash));
		playerProfileRepository.save(new PlayerProfile(account, username));

		authenticateSession(email, request.password(), httpRequest);
		return toResponse(account);
	}

	public AuthUserResponseDto login(LoginRequestDto request, HttpServletRequest httpRequest) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		try {
			authenticateSession(email, request.password(), httpRequest);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, GENERIC_LOGIN_ERROR);
		}
		UserAccount account = userAccountRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, GENERIC_LOGIN_ERROR));
		return toResponse(account);
	}

	public void logout(HttpServletRequest request) {
		SecurityContextHolder.clearContext();
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
	}

	public Optional<AuthUserResponseDto> currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
			return Optional.empty();
		}
		return userAccountRepository.findById(principal.getId()).map(this::toResponse);
	}

	public Map<String, Object> passwordResetStatus() {
		boolean mailEnabled = authProperties.getMail().isEnabled();
		return Map.of(
				"available", mailEnabled,
				"message", mailEnabled
						? "Password recovery is available."
						: "Password recovery is unavailable until an SMTP provider is configured.");
	}

	@Transactional
	public Map<String, Object> requestPasswordReset(PasswordResetRequestDto request) {
		if (!authProperties.getMail().isEnabled()) {
			return Map.of(
					"message",
					"Password recovery is unavailable until an SMTP provider is configured.");
		}

		String email = request.email().trim().toLowerCase(Locale.ROOT);
		Optional<UserAccount> account = userAccountRepository.findByEmailIgnoreCase(email);
		if (account.isPresent()) {
			String rawToken = generateRawToken();
			String tokenHash = sha256(rawToken);
			passwordResetTokenRepository.save(
					new PasswordResetToken(account.get(), tokenHash, Instant.now().plus(1, ChronoUnit.HOURS)));
			// SMTP delivery is intentionally not implemented until a provider is configured.
		}
		return Map.of("message", GENERIC_RESET_MESSAGE);
	}

	@Transactional
	public Map<String, Object> confirmPasswordReset(PasswordResetConfirmDto request) {
		List<String> passwordErrors = PasswordPolicy.validate(request.password(), request.confirmPassword());
		if (!passwordErrors.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", passwordErrors));
		}

		String tokenHash = sha256(request.token().trim());
		PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset link is invalid or expired."));
		Instant now = Instant.now();
		if (token.isUsed() || token.isExpired(now)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset link is invalid or expired.");
		}

		UserAccount account = token.getUserAccount();
		account.setPasswordHash(passwordEncoder.encode(request.password()));
		token.markUsed(now);
		userAccountRepository.save(account);
		passwordResetTokenRepository.save(token);
		return Map.of("message", "Password updated. You can log in with your new password.");
	}

	private void authenticateSession(String email, String password, HttpServletRequest httpRequest) {
		UsernamePasswordAuthenticationToken authRequest =
				UsernamePasswordAuthenticationToken.unauthenticated(email, password);
		Authentication authentication = authenticationManager.authenticate(authRequest);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		HttpSession session = httpRequest.getSession(true);
		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
	}

	private AuthUserResponseDto toResponse(UserAccount account) {
		return new AuthUserResponseDto(account.getId(), account.getUsername(), account.getEmail());
	}

	private String generateRawToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashed);
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to hash reset token", ex);
		}
	}
}
