-- Bounded consistent-improvement observations (separate from TVP / RCA / SCR / HLCR).
-- Tracks consecutive eligible first-attempt successes on distinct graded scenarios.
CREATE TABLE learning_concept_consistency (
    id UUID NOT NULL PRIMARY KEY,
    participant_code VARCHAR(64) NOT NULL,
    concept_tag VARCHAR(128) NOT NULL,
    mission_id VARCHAR(64) NOT NULL,
    source_challenge_id VARCHAR(64) NOT NULL,
    consecutive_successes INT NOT NULL DEFAULT 0,
    scenarios_used INT NOT NULL DEFAULT 0,
    observed_challenge_ids_json TEXT NOT NULL DEFAULT '[]',
    planned_challenge_ids_json TEXT NOT NULL DEFAULT '[]',
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_learning_concept_consistency_participant
    ON learning_concept_consistency (participant_code, mission_id, status);

CREATE INDEX idx_learning_concept_consistency_open
    ON learning_concept_consistency (participant_code, concept_tag, status);
