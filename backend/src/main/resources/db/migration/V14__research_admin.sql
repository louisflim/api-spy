-- Research reporting (SRS UC-07, NFR-S2, NFR-P3).
-- 1) Account role: only ADMINISTRATOR accounts may open the research console.
ALTER TABLE user_accounts
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'PLAYER';

-- 2) Hidden gameplay event log: one row per room-attempt event. Never shown to players.
--    participant_code is the pseudonymous research id (account UUID, shown as a P- code).
CREATE TABLE research_events (
    id UUID NOT NULL PRIMARY KEY,
    participant_code VARCHAR(64) NOT NULL,
    mission_id VARCHAR(64) NOT NULL,
    module INT NOT NULL,
    rank_code VARCHAR(32) NOT NULL,
    level INT NOT NULL,
    room_generation UUID,
    event_type VARCHAR(32) NOT NULL,
    reason VARCHAR(32),
    device_id VARCHAR(64),
    run_kind VARCHAR(16),
    api_reset_count INT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_research_events_participant ON research_events (participant_code);
CREATE INDEX idx_research_events_mission ON research_events (mission_id);
