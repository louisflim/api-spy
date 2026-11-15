import type { CombatTuning } from './types'
import { COMBAT_TUNING } from './combatTuning'

export type HealthChangeListener = (hearts: number, maxHearts: number) => void
export type DeathListener = () => void

/**
 * Temporary run hearts only — never written as career progress.
 * Invulnerability window after each successful hit.
 */
export class PlayerHealth {
  private hearts: number
  private readonly maxHearts: number
  private invulnerableUntil = 0
  private dead = false
  private onChange: HealthChangeListener | null = null
  private onDeath: DeathListener | null = null

  constructor(tuning: CombatTuning = COMBAT_TUNING) {
    this.maxHearts = tuning.maxHearts
    this.hearts = tuning.maxHearts
  }

  getHearts(): number {
    return this.hearts
  }

  getMaxHearts(): number {
    return this.maxHearts
  }

  isDead(): boolean {
    return this.dead
  }

  isInvulnerable(nowMs: number): boolean {
    return nowMs < this.invulnerableUntil
  }

  setListeners(onChange: HealthChangeListener, onDeath: DeathListener): void {
    this.onChange = onChange
    this.onDeath = onDeath
    this.onChange(this.hearts, this.maxHearts)
  }

  /** Restore full temporary hearts (combat restart / fresh room). */
  reset(): void {
    this.hearts = this.maxHearts
    this.invulnerableUntil = 0
    this.dead = false
    this.onChange?.(this.hearts, this.maxHearts)
  }

  /**
   * Apply enemy damage. Returns true when a heart was removed.
   * No-ops during invulnerability or after death.
   */
  takeDamage(amount: number, nowMs: number, invulnerabilityMs: number): boolean {
    if (this.dead || amount <= 0) return false
    if (this.isInvulnerable(nowMs)) return false
    this.hearts = Math.max(0, this.hearts - amount)
    this.invulnerableUntil = nowMs + invulnerabilityMs
    this.onChange?.(this.hearts, this.maxHearts)
    if (this.hearts <= 0) {
      this.dead = true
      this.onDeath?.()
    }
    return true
  }
}
