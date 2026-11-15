-- Module 0 Agent Training Academy orientation progress (per authenticated player).
CREATE TABLE module0_orientation_progress (
    id UUID NOT NULL PRIMARY KEY,
    participant_code VARCHAR(64) NOT NULL,
    last_topic_id VARCHAR(64) NOT NULL,
    completed_topic_ids_json TEXT NOT NULL,
    completed_steps_json TEXT NOT NULL,
    orientation_completed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_module0_orientation_participant UNIQUE (participant_code)
);

CREATE INDEX idx_module0_orientation_participant
    ON module0_orientation_progress (participant_code);
