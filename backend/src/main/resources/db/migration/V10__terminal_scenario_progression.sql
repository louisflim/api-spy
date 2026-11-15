-- Server-authoritative terminal verification sequence for automatic scenario progression.
-- Combat-only resets keep these; API-driven / replay room runs clear and reselect.

ALTER TABLE mission_progress
    ADD COLUMN active_terminal_challenge_id VARCHAR(128);

ALTER TABLE mission_progress
    ADD COLUMN completed_terminal_challenge_ids_json TEXT NOT NULL DEFAULT '[]';
