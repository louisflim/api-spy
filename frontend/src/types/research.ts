/** Shapes returned by /api/admin (research console). */
export interface Rate {
  numerator: number
  denominator: number
  percent: number | null
}

export interface Ratio {
  numerator: number
  denominator: number
  value: number | null
}

export interface CellSummary {
  participants: number
  attempts: number
  inProgress: number
  missionsAttempted: number
  missionsCompleted: number
  completions: number
  apiResets: number
  voluntaryRestarts: number
  combatRestarts: number
  abandoned: number
  combatDeaths: number
  bossAttempts: number
  bossDefeats: number
  bossCombatDeaths: number
  unsubmittedDecisions: number
}

export interface KpiCell {
  SCR: Rate
  RCA: Rate
  HLCR: Rate | null
  ARC: Ratio
  abandonment: Rate
  summary: CellSummary
}

export type ResearchRank = 'RECRUIT' | 'FIELD_AGENT' | 'SPECIAL_AGENT'

export interface MissionKpiRow extends Omit<KpiCell, 'RCA' | 'HLCR'> {
  missionId: string
  module: number
  rank: ResearchRank
  level: number
}

export interface KpiReport {
  generatedAt: string
  participantFilter: string | null
  participants: number
  hasData: boolean
  rca: {
    overall: Rate
    unsubmittedCountedIncorrect: number
    byConcept: Record<string, Rate>
    firstUse: Rate
    laterModuleRecall: Rate
  }
  modules: Array<{ module: number; total: KpiCell; ranks: Record<ResearchRank, KpiCell> }>
  rankProgression: Record<ResearchRank, { participantsStarted: number; participantsFinishedRank: number; completion: Rate }>
  missions: MissionKpiRow[]
}

export interface ParticipantRow {
  participant: string
  attempts: number
  completions: number
  decisions: number
  firstSeen: string | null
  lastSeen: string | null
}
