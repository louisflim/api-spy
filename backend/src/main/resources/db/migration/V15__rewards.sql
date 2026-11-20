-- Gameplay rewards (proposal: EXP from enemy defeats upgrades guns; coins from first clears
-- (and later chests) buy guns). Rewards are gameplay variables only: they never enter
-- SCR, RCA or HLCR and never affect API validation.

-- 1) One wallet per agent: balances, gun upgrade levels and owned/equipped guns.
CREATE TABLE agent_rewards (
    participant_code VARCHAR(64) NOT NULL PRIMARY KEY,
    exp INT NOT NULL DEFAULT 0,
    exp_earned INT NOT NULL DEFAULT 0,
    coins INT NOT NULL DEFAULT 0,
    coins_earned INT NOT NULL DEFAULT 0,
    damage_level INT NOT NULL DEFAULT 0,
    fire_rate_level INT NOT NULL DEFAULT 0,
    velocity_level INT NOT NULL DEFAULT 0,
    owned_weapons VARCHAR(256) NOT NULL DEFAULT 'starter_sidearm',
    equipped_weapon VARCHAR(64) NOT NULL DEFAULT 'starter_sidearm',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 2) Append-only ledger of every grant and spend. Used for anti-farming caps (per room run
--    and per mission) and as the hidden log of kills, purchases and upgrades.
--    dedupe_key makes one-time grants (first-clear coins, boss EXP) idempotent.
CREATE TABLE reward_ledger (
    id UUID NOT NULL PRIMARY KEY,
    participant_code VARCHAR(64) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    amount INT NOT NULL,
    mission_id VARCHAR(64),
    room_generation UUID,
    detail VARCHAR(64),
    dedupe_key VARCHAR(160) UNIQUE,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_reward_ledger_participant_mission ON reward_ledger (participant_code, mission_id);
