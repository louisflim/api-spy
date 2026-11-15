import type { EnemyAiState } from './types'

export type StateEnterHandler = (prev: EnemyAiState, next: EnemyAiState) => void

/** Lightweight FSM for Security Patrol Guard AI. */
export class EnemyStateMachine {
  private state: EnemyAiState = 'PATROL'
  private onEnter: StateEnterHandler | null = null

  constructor(initial: EnemyAiState = 'PATROL') {
    this.state = initial
  }

  getState(): EnemyAiState {
    return this.state
  }

  setOnEnter(handler: StateEnterHandler): void {
    this.onEnter = handler
  }

  transition(next: EnemyAiState): boolean {
    if (this.state === next) return false
    const prev = this.state
    this.state = next
    this.onEnter?.(prev, next)
    return true
  }

  reset(initial: EnemyAiState = 'PATROL'): void {
    this.state = initial
  }
}
