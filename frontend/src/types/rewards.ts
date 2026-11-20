import type { GunUpgrades } from '../phaser/combat/weapons'

/** GET /api/rewards — gameplay rewards only, never an academic score. */
export interface RewardsState {
  exp: number
  coins: number
  expEarned: number
  coinsEarned: number
  maxUpgradeLevel: number
  equippedWeapon: string
  weapons: WeaponOffer[]
  /** False until the agent makes the free starter-character pick. */
  starterCharacterChosen: boolean
  characters: CharacterOffer[]
}

/** Starter character: one free pick at sign-up; the others cost coins. */
export interface CharacterOffer {
  id: string
  displayName: string
  price: number
  owned: boolean
}

export interface WeaponOffer {
  id: string
  displayName: string
  price: number
  owned: boolean
  /** This agent's upgrade levels for this gun. */
  upgrades: GunUpgrades
  /** EXP price of the next level of any attribute; index = current level. */
  upgradeCosts: number[]
}

export type GunStat = keyof GunUpgrades

/** Window event fired after rewards change outside the current screen (e.g. the free agent pick). */
export const REWARDS_CHANGED_EVENT = 'apispy:rewards-changed'
