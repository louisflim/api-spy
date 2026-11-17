-- Level 7 boss: combat defeat is recorded per room generation and is required (with all
-- mandatory API devices) before the exit opens. Boss combat never enters RCA.
ALTER TABLE mission_progress
    ADD COLUMN boss_defeated BOOLEAN NOT NULL DEFAULT FALSE;
