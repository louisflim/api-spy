-- Active room-run kind for replay: career COMPLETED stays permanent while REPLAY
-- holds temporary unlocks / roomGeneration for a fresh field attempt.

ALTER TABLE mission_progress
    ADD COLUMN active_run_kind VARCHAR(16) NOT NULL DEFAULT 'PRIMARY';

ALTER TABLE mission_progress
    ADD COLUMN replay_count INT NOT NULL DEFAULT 0;

-- Career-cleared rows are idle until the player starts an authorized replay.
UPDATE mission_progress
SET active_run_kind = 'NONE'
WHERE status = 'COMPLETED';
