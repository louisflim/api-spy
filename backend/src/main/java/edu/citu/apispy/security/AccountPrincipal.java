package edu.citu.apispy.security;

import edu.citu.apispy.domain.auth.UserAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AccountPrincipal implements UserDetails {

	private final UUID id;
	private final String username;
	private final String email;
	private final String passwordHash;
	private final boolean enabled;

	public AccountPrincipal(UserAccount account) {
		this.id = account.getId();
		this.username = account.getUsername();
		this.email = account.getEmail();
		this.passwordHash = account.getPasswordHash();
		this.enabled = account.isEnabled();
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_PLAYER"));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	public String getDisplayUsername() {
		return username;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}
}
