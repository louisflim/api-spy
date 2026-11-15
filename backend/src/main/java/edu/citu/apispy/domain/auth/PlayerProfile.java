package edu.citu.apispy.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_profiles")
public class PlayerProfile {

	@Id
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_account_id", nullable = false, unique = true)
	private UserAccount userAccount;

	@Column(name = "display_name", nullable = false, length = 64)
	private String displayName;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PlayerProfile() {
	}

	public PlayerProfile(UserAccount userAccount, String displayName) {
		this.id = UUID.randomUUID();
		this.userAccount = userAccount;
		this.displayName = displayName;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UserAccount getUserAccount() {
		return userAccount;
	}

	public String getDisplayName() {
		return displayName;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
