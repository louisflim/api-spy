import { useEffect, useMemo, useState } from 'react'
import { fetchAllMissionProgress } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import {
  clearedMissionIds,
  loadSelectedOutfitId,
  outfitStatus,
  OUTFITS,
  saveSelectedOutfitId,
  type Outfit,
  type OutfitId,
} from '../content/outfits'
import '../theme/loadout.css'

const TIER_LABEL: Record<Outfit['tier'], string> = {
  starter: 'Starter',
  rank: 'Rank skin',
  legendary: 'Legendary',
}

/** Agent Loadout: pick the outfit used in field rooms. Locked skins show their proposal requirement. */
export function LoadoutScreen() {
  const { profile } = useAuth()
  const userId = profile?.userId ?? ''
  const [cleared, setCleared] = useState<Set<string> | null>(null)
  const [error, setError] = useState<string | null>(null)
  /** Set when the agent equips something this visit; otherwise read the saved choice. */
  const [chosen, setChosen] = useState<OutfitId | null>(null)
  const selected: OutfitId = chosen ?? (userId ? loadSelectedOutfitId(userId) : 'rookie')

  useEffect(() => {
    let live = true
    fetchAllMissionProgress()
      .then((rows) => live && setCleared(clearedMissionIds(rows)))
      .catch(() => live && setError('Could not load your mission record. Locked skins stay locked until it loads.'))
    return () => {
      live = false
    }
  }, [])

  const statuses = useMemo(
    () => OUTFITS.map((outfit) => ({ outfit, status: outfitStatus(outfit, cleared ?? new Set()) })),
    [cleared],
  )

  const equip = (id: OutfitId) => {
    if (!userId) return
    saveSelectedOutfitId(userId, id)
    setChosen(id)
  }

  return (
    <div className="loadout" role="main">
      <p className="dash-kicker">AGENT LOADOUT</p>
      <h1 className="dash-heading">Choose your field outfit</h1>
      <p className="dash-note">
        Outfits are cosmetic: they never change combat, API requests or your research record. Rank skins unlock
        after all 21 missions of that rank; the legendary skin unlocks after all 63.
      </p>
      {error ? <p className="dash-note dash-note--warn">{error}</p> : null}
      <ul className="loadout__grid">
        {statuses.map(({ outfit, status }) => {
          const equipped = selected === outfit.id && status.unlocked
          return (
            <li
              key={outfit.id}
              className={`loadout-card loadout-card--${outfit.tier} ${status.unlocked ? '' : 'is-locked'} ${
                equipped ? 'is-equipped' : ''
              }`.trim()}
            >
              <span className="loadout-card__tier">{TIER_LABEL[outfit.tier]}</span>
              <div
                className="loadout-card__sprite"
                style={{ backgroundImage: `url('${outfit.sheet}')` }}
                role="img"
                aria-label={`${outfit.name} preview`}
              />
              <h2 className="loadout-card__name">{outfit.name}</h2>
              <p className="loadout-card__desc">{outfit.description}</p>
              {status.unlocked ? (
                <button
                  type="button"
                  className="loadout-card__equip"
                  disabled={equipped || !userId}
                  onClick={() => equip(outfit.id)}
                >
                  {equipped ? 'Equipped' : 'Equip'}
                </button>
              ) : (
                <div className="loadout-card__lock">
                  <p>
                    <span aria-hidden="true">🔒</span> {status.requirement}
                  </p>
                  <div
                    className="loadout-card__bar"
                    role="progressbar"
                    aria-valuemin={0}
                    aria-valuemax={status.required}
                    aria-valuenow={status.completed}
                    aria-label={`${status.completed} of ${status.required} missions`}
                  >
                    <span style={{ width: `${(100 * status.completed) / Math.max(1, status.required)}%` }} />
                  </div>
                  <p className="loadout-card__count">
                    {cleared ? `${status.completed} / ${status.required}` : 'Loading…'}
                  </p>
                </div>
              )}
            </li>
          )
        })}
      </ul>
    </div>
  )
}
