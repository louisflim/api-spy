CREATE TABLE user_accounts (
    id UUID NOT NULL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_user_accounts_username UNIQUE (username),
    CONSTRAINT uq_user_accounts_email UNIQUE (email)
);

CREATE TABLE player_profiles (
    id UUID NOT NULL PRIMARY KEY,
    user_account_id UUID NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_player_profiles_user UNIQUE (user_account_id),
    CONSTRAINT fk_player_profiles_user
        FOREIGN KEY (user_account_id) REFERENCES user_accounts (id)
);

CREATE TABLE password_reset_tokens (
    id UUID NOT NULL PRIMARY KEY,
    user_account_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_user
        FOREIGN KEY (user_account_id) REFERENCES user_accounts (id)
);

CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_account_id);
