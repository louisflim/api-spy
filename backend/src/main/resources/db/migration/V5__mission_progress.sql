-- Persistent escape-room / mission progression (no stars, XP, coins, or rewards).
CREATE TABLE mission_progress (
    id UUID NOT NULL PRIMARY KEY,
    participant_code VARCHAR(64) NOT NULL,
    mission_id VARCHAR(64) NOT NULL,
    module INT NOT NULL,
    rank_code VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    api_reset_count INT NOT NULL,
    required_device_count INT NOT NULL,
    unlocked_devices_json TEXT NOT NULL,
    final_lock_required BOOLEAN NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_mission_progress_player_mission UNIQUE (participant_code, mission_id)
);

CREATE INDEX idx_mission_progress_participant
    ON mission_progress (participant_code);

CREATE INDEX idx_mission_progress_status
    ON mission_progress (participant_code, status);
