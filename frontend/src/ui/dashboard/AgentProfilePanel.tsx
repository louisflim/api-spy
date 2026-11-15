import { SpyPanel } from '../spy/SpyPanel'

interface AgentProfilePanelProps {
  username: string
  /** Catalog difficulty track currently used for briefing (not an earned award). */
  difficultyTrack: string
  missionsCompleted: number | null
  missionsInProgress: number | null
  missionsAvailable: number | null
  missionsLocked: number | null
  delayMs?: number
}

export function AgentProfilePanel({
  username,
  difficultyTrack,
  missionsCompleted,
  missionsInProgress,
  missionsAvailable,
  missionsLocked,
  delayMs = 80,
}: AgentProfilePanelProps) {
  return (
    <SpyPanel variant="command" className="dash-panel dash-panel--profile" delayMs={delayMs} stamp="ID">
      <p className="dash-kicker">Agent dossier</p>
      <h2 className="dash-heading">Agent {username}</h2>
      <p className="dash-rank-line">
        Difficulty track: <strong>{difficultyTrack}</strong>
      </p>
      <p className="dash-note">
        Difficulty tracks (Recruit, Field Agent, Special Agent) select mission challenge. They are not
        earned progression awards.
      </p>

      <ul className="dash-stat-list" aria-label="Mission progress">
        <li>
          <span>Available</span>
          <strong>{missionsAvailable === null ? '—' : missionsAvailable}</strong>
        </li>
        <li>
          <span>In progress</span>
          <strong>{missionsInProgress === null ? '—' : missionsInProgress}</strong>
        </li>
        <li>
          <span>Completed</span>
          <strong>{missionsCompleted === null ? '—' : missionsCompleted}</strong>
        </li>
        <li>
          <span>Locked</span>
          <strong>{missionsLocked === null ? '—' : missionsLocked}</strong>
        </li>
      </ul>

      <div className="dash-placeholder-block">
        <h3>Experience &amp; coins</h3>
        <p>Not tracked yet. Values will appear here only when real account data exists.</p>
      </div>

      <div className="dash-placeholder-block">
        <h3>Medals &amp; rewards</h3>
        <p>No medals have been awarded. This section stays empty until rewards are implemented.</p>
      </div>
    </SpyPanel>
  )
}
