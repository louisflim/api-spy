-- Gun upgrades become per gun (each gun has its own Damage / Fire rate / Round speed levels and
-- its own EXP prices). Stored as "weaponId:damage:fireRate:velocity" entries, comma-separated.
ALTER TABLE agent_rewards
    ADD COLUMN gun_upgrades VARCHAR(512) NOT NULL DEFAULT '';

-- Levels already bought under the old shared upgrades move to the Field Pistol.
UPDATE agent_rewards
SET gun_upgrades = 'starter_sidearm:' || CAST(damage_level AS VARCHAR(4)) || ':'
        || CAST(fire_rate_level AS VARCHAR(4)) || ':' || CAST(velocity_level AS VARCHAR(4))
WHERE damage_level + fire_rate_level + velocity_level > 0;

ALTER TABLE agent_rewards DROP COLUMN damage_level;
ALTER TABLE agent_rewards DROP COLUMN fire_rate_level;
ALTER TABLE agent_rewards DROP COLUMN velocity_level;
