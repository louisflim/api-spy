-- Immutable first-attempt records for eligible mandatory API challenges (RCA source).
CREATE TABLE learning_first_attempts (
    id UUID NOT NULL PRIMARY KEY,
    participant_code VARCHAR(64) NOT NULL,
    mission_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    challenge_id VARCHAR(64) NOT NULL,
    variant_id VARCHAR(64) NOT NULL,
    concept_tags_json TEXT NOT NULL,
    method VARCHAR(16) NOT NULL,
    path VARCHAR(512) NOT NULL,
    headers_json TEXT NOT NULL,
    body TEXT NOT NULL,
    error_codes_json TEXT NOT NULL,
    first_attempt_correct BOOLEAN NOT NULL,
    concept_correct_json TEXT NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_learning_first_attempt
        UNIQUE (participant_code, mission_id, device_id)
);

CREATE INDEX idx_learning_first_attempts_participant
    ON learning_first_attempts (participant_code);

-- Concept stages: INTRODUCED | PRACTICING | IMPROVING (upgrade-only).
CREATE TABLE learning_concept_progress (
    id UUID NOT NULL PRIMARY KEY,
    participant_code VARCHAR(64) NOT NULL,
    concept_id VARCHAR(128) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_learning_concept_progress
        UNIQUE (participant_code, concept_id)
);

CREATE INDEX idx_learning_concept_progress_participant
    ON learning_concept_progress (participant_code);
