import { useEffect, useState } from 'react'
import { chooseStarterCharacter, fetchRewards } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { findOutfit, saveSelectedOutfitId, type OutfitId } from '../../content/outfits'
import { publicErrorMessage } from '../../security/publicErrors'
import { REWARDS_CHANGED_EVENT, type CharacterOffer } from '../../types/rewards'
import '../../theme/loadout.css'

/**
 * First sign-in: the new agent picks one starter character for free. The other two can be
 * bought later with in-game coins in Agent Loadout. Shown only until the pick is made.
 */
export function StarterAgentPicker() {
  const { profile } = useAuth()
  const userId = profile?.userId ?? ''
  const [offers, setOffers] = useState<CharacterOffer[] | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!userId) return
    let live = true
    fetchRewards()
      .then((rewards) => live && setOffers(rewards.starterCharacterChosen ? null : rewards.characters))
      .catch(() => undefined)
    return () => {
      live = false
    }
  }, [userId])

  if (!offers) return null

  const choose = (id: string) => {
    setBusy(id)
    setError(null)
    chooseStarterCharacter(id)
      .then(() => {
        saveSelectedOutfitId(userId, id as OutfitId)
        setOffers(null)
        // Let open screens (Loadout, dashboard) refresh their copy of the rewards.
        window.dispatchEvent(new Event(REWARDS_CHANGED_EVENT))
      })
      .catch((err) => setError(publicErrorMessage(err, 'Could not choose this agent. Try again.')))
      .finally(() => setBusy(null))
  }

  return (
    <div className="starter-pick" role="dialog" aria-modal="true" aria-labelledby="starter-pick-title">
      <div className="starter-pick__panel">
        <p className="dash-kicker">NEW AGENT</p>
        <h2 id="starter-pick-title" className="dash-heading">
          Choose your agent
        </h2>
        <p className="dash-note">
          Pick one agent for free. You can buy the other two later with coins in Agent Loadout. Agents are
          cosmetic only.
        </p>
        {error ? <p className="dash-note dash-note--warn">{error}</p> : null}
        <ul className="loadout__grid starter-pick__grid">
          {offers.map((offer) => {
            const outfit = findOutfit(offer.id)
            return (
              <li key={offer.id} className="loadout-card">
                <div
                  className="loadout-card__sprite"
                  style={{ backgroundImage: `url('${outfit.sheet}')` }}
                  role="img"
                  aria-label={`${offer.displayName} preview`}
                />
                <h3 className="loadout-card__name">{offer.displayName}</h3>
                <p className="loadout-card__desc">{outfit.description}</p>
                <button
                  type="button"
                  className="loadout-card__equip"
                  disabled={busy !== null}
                  onClick={() => choose(offer.id)}
                >
                  {busy === offer.id ? 'Choosing…' : 'Choose for free'}
                </button>
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}
