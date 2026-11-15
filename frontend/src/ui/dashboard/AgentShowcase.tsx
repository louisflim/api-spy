import { RadarSweep } from '../spy/RadarSweep'
import { RookieIdlePortrait } from '../spy/RookieIdlePortrait'
import { SpyPanel } from '../spy/SpyPanel'

interface AgentShowcaseProps {
  delayMs?: number
}

export function AgentShowcase({ delayMs = 140 }: AgentShowcaseProps) {
  return (
    <SpyPanel variant="command" className="dash-panel dash-panel--showcase" delayMs={delayMs} stamp="UNIT">
      <p className="dash-kicker">Field unit</p>
      <h2 className="dash-heading">Agent Loadout Preview</h2>
      <div className="dash-showcase">
        <div className="dash-showcase__radar">
          <RadarSweep />
        </div>
        <RookieIdlePortrait size="large" />
      </div>
      <p className="dash-note">
        Rookie Detective is the only playable field sprite. Cosmetics and alternate loadouts are not
        available yet.
      </p>
    </SpyPanel>
  )
}
