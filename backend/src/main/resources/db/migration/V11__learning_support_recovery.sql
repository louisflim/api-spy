-- Targeted teaching / practice recovery state on adaptive triggers.
-- Separate ADD COLUMN statements for H2 (PostgreSQL MODE) compatibility.
ALTER TABLE learning_adaptive_triggers ADD COLUMN review_lesson_id VARCHAR(64);
ALTER TABLE learning_adaptive_triggers ADD COLUMN lesson_opened BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE learning_adaptive_triggers ADD COLUMN used_practice_challenge_ids TEXT NOT NULL DEFAULT '[]';

-- Ungraded practice outcomes (separate from graded first-attempt / TVP evidence).
CREATE TABLE learning_practice_outcomes (
    id UUID NOT NULL PRIMARY KEY,
    participant_code VARCHAR(64) NOT NULL,
    concept_tag VARCHAR(128) NOT NULL,
    challenge_id VARCHAR(64) NOT NULL,
    room_mission_id VARCHAR(64),
    correct BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_learning_practice_outcomes_participant
    ON learning_practice_outcomes (participant_code, concept_tag);
