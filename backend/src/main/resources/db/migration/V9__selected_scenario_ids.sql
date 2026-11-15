-- Persist authored scenario selection for the active room run.
-- Combat-only resets keep this list; API-driven restarts may reselect from pools.

ALTER TABLE mission_progress
    ADD COLUMN selected_challenge_ids_json TEXT NOT NULL DEFAULT '[]';

ALTER TABLE mission_progress
    ADD COLUMN previous_challenge_ids_json TEXT NOT NULL DEFAULT '[]';

ALTER TABLE mission_progress
    ADD COLUMN incorrect_graded_attempts_since_reset INT NOT NULL DEFAULT 0;
