-- Per-decision concept outcomes (analysis history; does not overwrite first attempts).
CREATE TABLE learning_concept_decisions (
    id UUID NOT NULL PRIMARY KEY,
    participant_code VARCHAR(64) NOT NULL,
    mission_id VARCHAR(64) NOT NULL,
    rank_code VARCHAR(32) NOT NULL,
    challenge_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    concept_tag VARCHAR(128) NOT NULL,
    applicable BOOLEAN NOT NULL,
    correct BOOLEAN NOT NULL,
    error_codes_json TEXT NOT NULL,
    submission_hash VARCHAR(128) NOT NULL,
    first_eligible BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_learning_concept_decisions_participant
    ON learning_concept_decisions (participant_code);

CREATE INDEX idx_learning_concept_decisions_mission
    ON learning_concept_decisions (participant_code, mission_id);

-- Terminal verification evidence slots (unique; never deleted on later mistakes).
CREATE TABLE learning_terminal_verification (
    id UUID NOT NULL PRIMARY KEY,
    participant_code VARCHAR(64) NOT NULL,
    mission_id VARCHAR(64) NOT NULL,
    terminal_id VARCHAR(64) NOT NULL,
    challenge_id VARCHAR(64) NOT NULL,
    concept_tag VARCHAR(128) NOT NULL,
    earned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_learning_terminal_verification
        UNIQUE (participant_code, mission_id, challenge_id, concept_tag)
);

CREATE INDEX idx_learning_terminal_verification_terminal
    ON learning_terminal_verification (participant_code, mission_id, terminal_id);

-- Adaptive practice trigger dedupe (same evidence cannot re-fire).
CREATE TABLE learning_adaptive_triggers (
    id UUID NOT NULL PRIMARY KEY,
    participant_code VARCHAR(64) NOT NULL,
    concept_tag VARCHAR(128) NOT NULL,
    room_mission_id VARCHAR(64) NOT NULL,
    evidence_key VARCHAR(256) NOT NULL,
    practice_challenge_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_learning_adaptive_trigger_evidence
        UNIQUE (participant_code, concept_tag, evidence_key)
);

CREATE INDEX idx_learning_adaptive_triggers_room
    ON learning_adaptive_triggers (participant_code, room_mission_id);

-- Spaced concept review queue (later authored scenarios; not same-session mastery).
CREATE TABLE learning_concept_review_queue (
    id UUID NOT NULL PRIMARY KEY,
    participant_code VARCHAR(64) NOT NULL,
    concept_tag VARCHAR(128) NOT NULL,
    source_mission_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    review_challenge_id VARCHAR(64),
    outcome_correct BOOLEAN,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_learning_concept_review_participant
    ON learning_concept_review_queue (participant_code, status);
