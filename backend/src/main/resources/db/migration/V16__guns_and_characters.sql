-- Gun catalog v2 (Field Pistol free; AK-47 and Shotgun bought with coins) and starter characters
-- (one free pick at sign-up, the other two bought with in-game coins). Cosmetic and combat only.

-- 1) Rename the first-draft purchasable guns to the new catalog ids, keeping what agents own.
UPDATE agent_rewards SET owned_weapons = REPLACE(owned_weapons, 'pulse_carbine', 'assault_rifle');
UPDATE agent_rewards SET owned_weapons = REPLACE(owned_weapons, 'breach_cannon', 'combat_shotgun');
UPDATE agent_rewards SET equipped_weapon = 'assault_rifle' WHERE equipped_weapon = 'pulse_carbine';
UPDATE agent_rewards SET equipped_weapon = 'combat_shotgun' WHERE equipped_weapon = 'breach_cannon';

-- 2) Starter characters owned by the agent (comma-separated ids). Empty = free pick not made yet.
ALTER TABLE agent_rewards
    ADD COLUMN owned_characters VARCHAR(256) NOT NULL DEFAULT '';
