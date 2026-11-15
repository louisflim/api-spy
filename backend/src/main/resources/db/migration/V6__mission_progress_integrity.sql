-- Room-instance binding and server-side graded attempt tracking for completion/reset integrity.
-- Physical Phaser position is still client-enforced; room_generation binds complete/reset to the active room attempt.

ALTER TABLE mission_progress
    ADD COLUMN room_generation UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE mission_progress
    ADD COLUMN graded_attempt_count_since_reset INT NOT NULL DEFAULT 0;

ALTER TABLE mission_progress
    ADD COLUMN combat_restart_authorized BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE mission_progress
    ALTER COLUMN room_generation DROP DEFAULT;
