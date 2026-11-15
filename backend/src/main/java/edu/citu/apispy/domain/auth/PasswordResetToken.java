package edu.citu.apispy.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_account_id", nullable = false)
	private UserAccount userAccount;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PasswordResetToken() {
	}

	public PasswordResetToken(UserAccount userAccount, String tokenHash, Instant expiresAt) {
		this.id = UUID.randomUUID();
		this.userAccount = userAccount;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UserAccount getUserAccount() {
		return userAccount;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

	public boolean isUsed() {
		return usedAt != null;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public void markUsed(Instant at) {
		this.usedAt = at;
	}
}
