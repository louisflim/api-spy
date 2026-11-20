import { useCallback, useEffect, useMemo, useState } from 'react'
import { chooseStarterCharacter, fetchAllMissionProgress, fetchRewards, purchaseCharacter } from '../api/client'
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
import { publicErrorMessage } from '../security/publicErrors'
import { REWARDS_CHANGED_EVENT, type RewardsState } from '../types/rewards'
import { ArmoryPanel } from '../ui/loadout/ArmoryPanel'
import '../theme/loadout.css'

const TIER_LABEL: Record<Outfit['tier'], string> = {
  starter: 'Agent',
  rank: 'Rank skin',
  legendary: 'Legendary',
}

/**
 * Agent Loadout: pick the agent used in field rooms and manage guns in the Armory.
 * Starter agents: one free pick, the others bought with coins. Rank / legendary skins and gun
 * skins unlock from mission completion (proposal). Guns: free pistol, others bought with coins.
 */
export function LoadoutScreen() {
  const { profile } = useAuth()
  const userId = profile?.userId ?? ''
  const [cleared, setCleared] = useState<Set<string> | null>(null)
  const [rewards, setRewards] = useState<RewardsState | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  /** Set when the agent equips something this visit; otherwise read the saved choice. */
  const [chosen, setChosen] = useState<OutfitId | null>(null)
  const selected: OutfitId = chosen ?? (userId ? loadSelectedOutfitId(userId) : 'rookie')

  const loadRewards = useCallback(() => {
    fetchRewards()
      .then(setRewards)
      .catch(() => setError('Could not load your agents and armory. Try again in a moment.'))
  }, [])

  useEffect(() => {
    let live = true
    fetchAllMissionProgress()
      .then((rows) => live && setCleared(clearedMissionIds(rows)))
      .catch(() => live && setError('Could not load your mission record. Locked skins stay locked until it loads.'))
    loadRewards()
    window.addEventListener(REWARDS_CHANGED_EVENT, loadRewards)
    return () => {
      live = false
      window.removeEventListener(REWARDS_CHANGED_EVENT, loadRewards)
    }
  }, [loadRewards])

  const owned = useMemo(
    () => (rewards ? new Set(rewards.characters.filter((c) => c.owned).map((c) => c.id)) : null),
    [rewards],
  )

  const statuses = useMemo(
    () => OUTFITS.map((outfit) => ({ outfit, status: outfitStatus(outfit, cleared ?? new Set(), owned) })),
    [cleared, owned],
  )

  const equip = (id: OutfitId) => {
    if (!userId) return
    saveSelectedOutfitId(userId, id)
    setChosen(id)
  }

  const acquire = (id: OutfitId, free: boolean) => {
    setBusy(id)
    setError(null)
    const request = free ? chooseStarterCharacter(id) : purchaseCharacter(id)
    request
      .then((next) => {
        setRewards(next)
        equip(id)
      })
      .catch((err) => setError(publicErrorMessage(err, 'That did not work. Try again.')))
      .finally(() => setBusy(null))
  }

  return (
    <div className="loadout" role="main">
      <div className="loadout__head">
        <div>
          <p className="dash-kicker">AGENT LOADOUT</p>
          <h1 className="dash-heading">Choose your agent</h1>
        </div>
        {rewards ? (
          <div className="armory__wallet loadout__wallet" aria-label="Balances">
            <span>
              EXP <strong>{rewards.exp}</strong>
            </span>
            <span className="armory__coins">
              <span aria-hidden="true">◈</span> <strong>{rewards.coins}</strong> coins
            </span>
          </div>
        ) : null}
      </div>
      <p className="dash-note">
        Pick one agent for free; buy the other agents with coins. Rank skins unlock after all 21 missions of
        that rank and the legendary skin after all 63. Agents are cosmetic: they never change combat, API
        requests or your research record.
      </p>
      {error ? <p className="dash-note dash-note--warn">{error}</p> : null}
      <ul className="loadout__grid">
        {statuses.map(({ outfit, status }) => {
          const equipped = selected === outfit.id && status.unlocked
          const offer = rewards?.characters.find((c) => c.id === outfit.id)
          const isCharacter = outfit.unlock.kind === 'character'
          const short = rewards && offer ? offer.price - rewards.coins : 0
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
              ) : isCharacter && rewards && !rewards.starterCharacterChosen ? (
                <button
                  type="button"
                  className="loadout-card__equip"
                  disabled={busy !== null}
                  onClick={() => acquire(outfit.id, true)}
                >
                  Choose for free
                </button>
              ) : isCharacter ? (
                <button
                  type="button"
                  className="loadout-card__equip loadout-card__buy"
                  disabled={!offer || short > 0 || busy !== null}
                  onClick={() => acquire(outfit.id, false)}
                  title={short > 0 ? `You need ${short} more coins` : undefined}
                >
                  {offer ? `Buy · ◈ ${offer.price}` : 'Loading…'}
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
      <ArmoryPanel rewards={rewards} onRewards={setRewards} cleared={cleared} loadError={error} />
    </div>
  )
}
