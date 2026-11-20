import { useState } from 'react'
import { equipWeapon, purchaseWeapon, upgradeGun } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import {
  GUN_SKINS,
  gunSkinStatus,
  gunSprite,
  loadSelectedGunSkinId,
  saveSelectedGunSkinId,
  type GunSkinId,
} from '../../content/gunSkins'
import { buildLoadoutWeapon, UPGRADE_EFFECT } from '../../phaser/combat/weapons'
import { publicErrorMessage } from '../../security/publicErrors'
import type { GunStat, RewardsState } from '../../types/rewards'

const UPGRADES: { stat: GunStat; name: string; effect: string }[] = [
  { stat: 'DAMAGE', name: 'Damage', effect: `+${UPGRADE_EFFECT.DAMAGE * 100}% damage per level` },
  { stat: 'FIRE_RATE', name: 'Fire rate', effect: `${UPGRADE_EFFECT.FIRE_RATE * 100}% less time between shots per level` },
  { stat: 'VELOCITY', name: 'Round speed', effect: `+${UPGRADE_EFFECT.VELOCITY * 100}% round speed per level` },
]

const GUN_NOTES: Record<string, string> = {
  starter_sidearm: 'Semi-automatic pistol. One shot per click.',
  assault_rifle: 'Fully automatic. Hold the mouse or Space to keep firing.',
  combat_shotgun: 'One blast per click that sprays pellets. Best up close.',
}

/** Bars are relative to the strongest gun in the catalog (each with this agent's own upgrades). */
function statBars(rewards: RewardsState) {
  const perShot = (g: { damage: number; pellets?: number }) => g.damage * (g.pellets ?? 1)
  const rate = (g: { cooldownMs: number; fireMode?: string }) =>
    1000 / (g.fireMode === 'semi' ? Math.max(g.cooldownMs, 250) : g.cooldownMs)
  const build = (id: string) => buildLoadoutWeapon(id, rewards.weapons.find((w) => w.id === id)?.upgrades)
  const guns = rewards.weapons.map((w) => build(w.id))
  const maxShot = Math.max(...guns.map(perShot))
  const maxRate = Math.max(...guns.map(rate))
  const maxDps = Math.max(...guns.map((g) => perShot(g) * rate(g)))
  return (id: string) => {
    const g = build(id)
    return [
      { label: 'Damage', value: perShot(g) / maxShot },
      { label: 'Fire rate', value: rate(g) / maxRate },
      { label: 'Power', value: (perShot(g) * rate(g)) / maxDps },
    ]
  }
}

interface ArmoryPanelProps {
  rewards: RewardsState | null
  onRewards: (next: RewardsState) => void
  /** Career-cleared missions (gun skin unlocks); null while loading. */
  cleared: Set<string> | null
  loadError?: string | null
}

/**
 * Armory: coins from first clears buy guns, EXP from enemy defeats upgrades them, and rank gun
 * skins (proposal) restyle the equipped gun. Ammo is unlimited. Gameplay only — never part of
 * the API record.
 */
export function ArmoryPanel({ rewards, onRewards, cleared, loadError = null }: ArmoryPanelProps) {
  const { profile } = useAuth()
  const userId = profile?.userId ?? ''
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [skinChoice, setSkinChoice] = useState<GunSkinId | null>(null)
  /** Gun shown in the upgrade bench (defaults to the equipped gun). */
  const [benchChoice, setBenchChoice] = useState<string | null>(null)
  const selectedSkin: GunSkinId = skinChoice ?? (userId ? loadSelectedGunSkinId(userId) : 'standard')

  const run = (key: string, action: () => Promise<RewardsState>) => {
    setBusy(key)
    setError(null)
    action()
      .then(onRewards)
      .catch((err) => setError(publicErrorMessage(err, 'That did not work. Try again.')))
      .finally(() => setBusy(null))
  }

  if (!rewards) {
    return (
      <section className="armory" aria-labelledby="armory-title">
        <h2 id="armory-title" className="armory__title">
          Armory
        </h2>
        <p className="dash-note">{loadError ?? 'Loading your armory…'}</p>
      </section>
    )
  }

  const bars = statBars(rewards)
  const owned = rewards.weapons.filter((w) => w.owned)
  const bench =
    owned.find((w) => w.id === benchChoice) ??
    owned.find((w) => w.id === rewards.equippedWeapon) ??
    owned[0] ??
    rewards.weapons[0]!
  const skinStatuses = GUN_SKINS.map((skin) => ({ skin, status: gunSkinStatus(skin, cleared ?? new Set()) }))
  const activeSkin = skinStatuses.find((s) => s.skin.id === selectedSkin && s.status.unlocked)?.skin.id ?? 'standard'

  return (
    <section className="armory" aria-labelledby="armory-title">
      <h2 id="armory-title" className="armory__title">
        Armory
      </h2>
      <p className="dash-note">
        Clear a mission for the first time to earn coins (10 Recruit, 20 Field Agent, 30 Special Agent) and buy
        guns or agents. Defeat enemies to earn EXP for gun upgrades. Ammo is always unlimited. Guns and upgrades
        only change combat; they never change API answers or your research record.
      </p>
      {error ? <p className="dash-note dash-note--warn">{error}</p> : null}

      <h3 className="armory__sub">Guns</h3>
      <ul className="armory__guns">
        {rewards.weapons.map((offer) => {
          const equipped = rewards.equippedWeapon === offer.id
          const affordable = rewards.coins >= offer.price
          const art = gunSprite(offer.id, activeSkin)
          return (
            <li key={offer.id} className={`armory-gun ${equipped ? 'is-equipped' : ''} ${offer.owned ? '' : 'is-locked'}`.trim()}>
              <div className="armory-gun__art">
                <img src={art.path} alt="" width={art.width} height={art.height} />
              </div>
              <h4 className="armory-gun__name">{offer.displayName}</h4>
              <p className="armory-gun__note">{GUN_NOTES[offer.id] ?? ''}</p>
              <dl className="armory-gun__stats">
                {bars(offer.id).map((bar) => (
                  <div key={bar.label} className="armory-gun__stat">
                    <dt>{bar.label}</dt>
                    <dd>
                      <span className="armory-gun__bar" style={{ width: `${Math.round(bar.value * 100)}%` }} />
                    </dd>
                  </div>
                ))}
              </dl>
              {offer.owned ? (
                <button
                  type="button"
                  className="loadout-card__equip"
                  disabled={equipped || busy !== null}
                  onClick={() => run(`equip-${offer.id}`, () => equipWeapon(offer.id))}
                >
                  {equipped ? 'Equipped' : 'Equip'}
                </button>
              ) : (
                <button
                  type="button"
                  className="loadout-card__equip armory__buy"
                  disabled={!affordable || busy !== null}
                  onClick={() => run(`buy-${offer.id}`, () => purchaseWeapon(offer.id))}
                  title={affordable ? undefined : `You need ${offer.price - rewards.coins} more coins`}
                >
                  Buy · ◈ {offer.price}
                </button>
              )}
            </li>
          )
        })}
      </ul>

      <h3 className="armory__sub">Gun skins (rank rewards, cosmetic)</h3>
      <ul className="gun-skins">
        {skinStatuses.map(({ skin, status }) => {
          const equipped = activeSkin === skin.id
          const art = gunSprite(rewards.equippedWeapon, skin.id)
          return (
            <li
              key={skin.id}
              className={`gun-skin ${equipped ? 'is-equipped' : ''} ${status.unlocked ? '' : 'is-locked'}`.trim()}
            >
              <div className="gun-skin__art">
                <img src={art.path} alt="" width={art.width} height={art.height} />
              </div>
              <h4 className="gun-skin__name">{skin.name}</h4>
              <p className="gun-skin__desc">{skin.description}</p>
              {status.unlocked ? (
                <button
                  type="button"
                  className="loadout-card__equip"
                  disabled={equipped || !userId}
                  onClick={() => {
                    saveSelectedGunSkinId(userId, skin.id)
                    setSkinChoice(skin.id)
                  }}
                >
                  {equipped ? 'Equipped' : 'Equip'}
                </button>
              ) : (
                <p className="gun-skin__desc">
                  <span aria-hidden="true">🔒</span> {status.requirement}
                  {cleared ? ` (${status.completed} / ${status.required})` : ''}
                </p>
              )}
            </li>
          )
        })}
      </ul>

      <h3 className="armory__sub">Gun upgrades (each gun is upgraded separately)</h3>
      <div className="armory__bench" role="tablist" aria-label="Gun to upgrade">
        {rewards.weapons.map((w) => (
          <button
            key={w.id}
            type="button"
            role="tab"
            aria-selected={bench.id === w.id}
            className={`armory__bench-tab ${bench.id === w.id ? 'is-active' : ''}`.trim()}
            disabled={!w.owned}
            title={w.owned ? undefined : 'Buy this gun to upgrade it'}
            onClick={() => setBenchChoice(w.id)}
          >
            {w.displayName}
            {w.owned ? '' : ' 🔒'}
          </button>
        ))}
      </div>
      <ul className="armory__upgrades">
        {UPGRADES.map(({ stat, name, effect }) => {
          const level = bench.upgrades[stat] ?? 0
          const maxed = level >= rewards.maxUpgradeLevel
          const cost = maxed ? null : bench.upgradeCosts[level]
          const affordable = cost !== null && cost !== undefined && rewards.exp >= cost
          return (
            <li key={stat} className="armory-upgrade">
              <div className="armory-upgrade__text">
                <strong>{name}</strong>
                <span>{effect}</span>
              </div>
              <span className="armory-upgrade__pips" aria-label={`Level ${level} of ${rewards.maxUpgradeLevel}`}>
                {Array.from({ length: rewards.maxUpgradeLevel }, (_, i) => (
                  <span key={i} className={i < level ? 'is-on' : ''} aria-hidden="true" />
                ))}
              </span>
              <button
                type="button"
                className="loadout-card__equip"
                disabled={maxed || !affordable || busy !== null}
                onClick={() => run(`up-${bench.id}-${stat}`, () => upgradeGun(bench.id, stat))}
              >
                {maxed ? 'Max level' : `Upgrade · ${cost} EXP`}
              </button>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
