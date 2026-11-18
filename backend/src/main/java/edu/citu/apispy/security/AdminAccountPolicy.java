package edu.citu.apispy.security;

import edu.citu.apispy.domain.auth.UserAccount;
import edu.citu.apispy.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides which accounts hold the ADMINISTRATOR role (SRS UC-07: "existing account assigned
 * the Administrator role"). Researchers register like any player; their e-mail addresses are
 * listed in {@code apispy.admin.emails} (env APISPY_ADMIN_EMAILS, comma separated).
 * Matching accounts are promoted at startup and on registration. Nobody can self-promote.
 */
@Component
public class AdminAccountPolicy implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminAccountPolicy.class);

	private final Set<String> adminEmails;
	private final UserAccountRepository accounts;

	public AdminAccountPolicy(
			@Value("${apispy.admin.emails:}") String adminEmails,
			UserAccountRepository accounts) {
		this.adminEmails = Arrays.stream(adminEmails.split(","))
				.map(e -> e.trim().toLowerCase(Locale.ROOT))
				.filter(e -> !e.isEmpty())
				.collect(Collectors.toUnmodifiableSet());
		this.accounts = accounts;
	}

	public boolean isAdministratorEmail(String email) {
		return email != null && adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
	}

	/** Applies the configured role to a (new or existing) account; returns true if it changed. */
	public boolean applyTo(UserAccount account) {
		String target = isAdministratorEmail(account.getEmail()) ? UserAccount.ROLE_ADMINISTRATOR : null;
		if (target != null && !target.equals(account.getRole())) {
			account.setRole(target);
			return true;
		}
		return false;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		for (String email : adminEmails) {
			accounts.findByEmailIgnoreCase(email).ifPresent(account -> {
				if (applyTo(account)) {
					accounts.save(account);
					log.info("Promoted research administrator account {}", account.getUsername());
				}
			});
		}
	}
}
