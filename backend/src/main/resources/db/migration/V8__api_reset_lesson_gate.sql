-- SRS v1.3: consecutive unresolved API-driven room attempts + forced ungraded lesson gate.
-- Combat-only deaths and non-escalating manual restarts do not increment this counter.

ALTER TABLE mission_progress
    ADD COLUMN consecutive_unresolved_api_resets INT NOT NULL DEFAULT 0;

ALTER TABLE mission_progress
    ADD COLUMN lesson_review_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE mission_progress
    ADD COLUMN required_review_lesson_id VARCHAR(64);

ALTER TABLE mission_progress
    ADD COLUMN lesson_review_completed_at TIMESTAMP WITH TIME ZONE;
