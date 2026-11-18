import { useCallback, useEffect, useState } from 'react'
import {
  downloadResearchCsv,
  fetchResearchKpi,
  fetchResearchParticipants,
  ResearchAccessError,
} from '../api/client'
import { RANK_LABELS } from '../content/moduleMeta'
import type { KpiCell, KpiReport, ParticipantRow, Rate, Ratio, ResearchRank } from '../types/research'
import '../theme/research.css'

const RANKS: ResearchRank[] = ['RECRUIT', 'FIELD_AGENT', 'SPECIAL_AGENT']

function pct(r: Rate | null | undefined) {
  if (!r) return <span className="research__muted">n/a</span>
  if (r.denominator === 0) return <span className="research__muted">—</span>
  return (
    <>
      {r.percent}% <span className="research__muted">({r.numerator}/{r.denominator})</span>
    </>
  )
}

function avg(r: Ratio) {
  if (r.denominator === 0) return <span className="research__muted">—</span>
  return (
    <>
      {r.value} <span className="research__muted">({r.numerator}/{r.denominator})</span>
    </>
  )
}

const missionLabel = (level: number) => (level === 7 ? 'Boss' : `L${level}`)

/**
 * Research console (SRS UC-07). Hidden from players: the API returns 403 for non-administrators
 * and this page then shows an access message instead of data. Read-only.
 */
export function ResearchConsoleScreen() {
  const [participant, setParticipant] = useState('')
  const [report, setReport] = useState<KpiReport | null>(null)
  const [people, setPeople] = useState<ParticipantRow[]>([])
  const [error, setError] = useState<string | null>(null)
  const [denied, setDenied] = useState(false)
  const [loading, setLoading] = useState(true)

  /** Fetches the report; state only changes when the requests settle. */
  const fetchReport = useCallback((code: string) => {
    Promise.all([fetchResearchKpi(code.trim()), fetchResearchParticipants()])
      .then(([kpi, rows]) => {
        setReport(kpi)
        setPeople(rows)
      })
      .catch((err) => {
        if (err instanceof ResearchAccessError) setDenied(true)
        else setError('Research report could not be loaded.')
      })
      .finally(() => setLoading(false))
  }, [])

  const load = (code: string) => {
    setLoading(true)
    setError(null)
    fetchReport(code)
  }

  useEffect(() => {
    fetchReport('')
  }, [fetchReport])

  const exportCsv = (kind: 'events' | 'first-attempts') => {
    downloadResearchCsv(kind).catch(() => setError('Export failed.'))
  }

  if (denied) {
    return (
      <div className="research" role="main">
        <p className="dash-kicker">RESEARCH CONSOLE</p>
        <h1 className="dash-heading">Administrator role required</h1>
        <p className="dash-note">
          Research reports are restricted to accounts assigned the Administrator role. Player accounts never see
          KPIs or research logs.
        </p>
      </div>
    )
  }

  const rows = (m: KpiReport['modules'][number]) =>
    [...RANKS.map((rank) => [RANK_LABELS[rank], m.ranks[rank]] as const), ['All ranks', m.total] as const]

  return (
    <div className="research" role="main">
      <p className="dash-kicker">RESEARCH CONSOLE · ADMINISTRATOR</p>
      <h1 className="dash-heading">KPI reports</h1>
      <p className="dash-note">
        Hidden from players. Read-only: viewing never changes gameplay records. Participants appear only as
        pseudonymous P- codes.
      </p>

      <form
        className="research__toolbar"
        onSubmit={(event) => {
          event.preventDefault()
          load(participant)
        }}
      >
        <label className="research__filter">
          <span>Participant</span>
          <select value={participant} onChange={(event) => setParticipant(event.target.value)}>
            <option value="">All participants</option>
            {people.map((p) => (
              <option key={p.participant} value={p.participant}>
                {p.participant}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" disabled={loading}>
          {loading ? 'Loading…' : 'Apply'}
        </button>
        <span className="research__spacer" />
        <button type="button" onClick={() => exportCsv('events')}>
          Export event log (CSV)
        </button>
        <button type="button" onClick={() => exportCsv('first-attempts')}>
          Export first attempts (CSV)
        </button>
      </form>

      {error ? <p className="dash-note dash-note--warn">{error}</p> : null}

      {report && !report.hasData ? (
        <div className="research__empty">
          <strong>Report data unavailable.</strong> No eligible gameplay has been recorded
          {report.participantFilter ? ` for ${report.participantFilter}` : ''} yet.
        </div>
      ) : null}

      {report && report.hasData ? (
        <>
          <p className="research__headline">
            Participants: <strong>{report.participants}</strong> · Overall RCA: <strong>{pct(report.rca.overall)}</strong>
            {report.rca.unsubmittedCountedIncorrect > 0 ? (
              <span className="research__muted">
                {' '}
                · {report.rca.unsubmittedCountedIncorrect} opened-but-unsubmitted decision(s) counted incorrect
              </span>
            ) : null}
          </p>

          <h2 className="research__h2">Primary KPIs by module and rank</h2>
          <div className="research__table-wrap">
            <table className="research__table">
              <thead>
                <tr>
                  <th>Module</th>
                  <th>Rank</th>
                  <th>SCR</th>
                  <th>RCA</th>
                  <th>HLCR</th>
                  <th>ARC</th>
                  <th>Abandonment</th>
                </tr>
              </thead>
              <tbody>
                {report.modules.map((m) =>
                  rows(m).map(([label, cell], i) => (
                    <tr key={`${m.module}-${label}`} className={i === 3 ? 'is-total' : ''}>
                      {i === 0 ? <th rowSpan={4}>Module {m.module}</th> : null}
                      <td>{label}</td>
                      <td>{pct(cell.SCR)}</td>
                      <td>{pct(cell.RCA)}</td>
                      <td>{pct(cell.HLCR)}</td>
                      <td>{avg(cell.ARC)}</td>
                      <td>{pct(cell.abandonment)}</td>
                    </tr>
                  )),
                )}
              </tbody>
            </table>
          </div>
          <p className="research__defs">
            <strong>SCR</strong> distinct missions completed ÷ attempted (per participant; replays excluded).{' '}
            <strong>RCA</strong> eligible mandatory decisions correct on first submission (final locks excluded;
            opened-but-unsubmitted terminals count as incorrect). <strong>HLCR</strong> Special Agent Levels 5–7.{' '}
            <strong>ARC</strong> (API-driven resets + voluntary restarts + abandoned) ÷ missions attempted; combat
            restarts listed separately. <strong>Abandonment</strong> attempts left with no outcome for 30+ minutes.
          </p>

          <h2 className="research__h2">Supporting gameplay summaries</h2>
          <div className="research__table-wrap">
            <table className="research__table">
              <thead>
                <tr>
                  <th>Module</th>
                  <th>Rank</th>
                  <th>Players</th>
                  <th>Attempts</th>
                  <th>Completions</th>
                  <th>API resets</th>
                  <th>Voluntary restarts</th>
                  <th>Abandoned</th>
                  <th>Combat deaths</th>
                  <th>Combat restarts</th>
                  <th>Boss: attempts / defeats / deaths</th>
                </tr>
              </thead>
              <tbody>
                {report.modules.map((m) =>
                  rows(m).map(([label, cell]: readonly [string, KpiCell], i) => {
                    const s = cell.summary
                    return (
                      <tr key={`${m.module}-${label}`} className={i === 3 ? 'is-total' : ''}>
                        {i === 0 ? <th rowSpan={4}>Module {m.module}</th> : null}
                        <td>{label}</td>
                        <td>{s.participants}</td>
                        <td>{s.attempts}</td>
                        <td>{s.completions}</td>
                        <td>{s.apiResets}</td>
                        <td>{s.voluntaryRestarts}</td>
                        <td>{s.abandoned}</td>
                        <td>{s.combatDeaths}</td>
                        <td>{s.combatRestarts}</td>
                        <td>
                          {s.bossAttempts} / {s.bossDefeats} / {s.bossCombatDeaths}
                        </td>
                      </tr>
                    )
                  }),
                )}
              </tbody>
            </table>
          </div>

          <div className="research__split">
            <section>
              <h2 className="research__h2">Rank progression</h2>
              <table className="research__table">
                <thead>
                  <tr>
                    <th>Rank</th>
                    <th>Started</th>
                    <th>Finished all 21</th>
                    <th>Mission completion</th>
                  </tr>
                </thead>
                <tbody>
                  {RANKS.map((rank) => {
                    const p = report.rankProgression[rank]
                    return (
                      <tr key={rank}>
                        <td>{RANK_LABELS[rank]}</td>
                        <td>{p.participantsStarted}</td>
                        <td>{p.participantsFinishedRank}</td>
                        <td>{pct(p.completion)}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </section>
            <section>
              <h2 className="research__h2">RCA breakdowns (descriptive)</h2>
              <table className="research__table">
                <tbody>
                  <tr>
                    <td>First independent use</td>
                    <td>{pct(report.rca.firstUse)}</td>
                  </tr>
                  <tr>
                    <td>Later-module recall</td>
                    <td>{pct(report.rca.laterModuleRecall)}</td>
                  </tr>
                  {Object.entries(report.rca.byConcept).map(([tag, rate]) => (
                    <tr key={tag}>
                      <td>{tag}</td>
                      <td>{pct(rate)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          </div>

          <details className="research__details">
            <summary>Per-mission retries ({report.missions.length} missions attempted)</summary>
            <div className="research__table-wrap">
              <table className="research__table">
                <thead>
                  <tr>
                    <th>Mission</th>
                    <th>Players</th>
                    <th>SCR</th>
                    <th>Attempts</th>
                    <th>API resets</th>
                    <th>Abandoned</th>
                    <th>Combat deaths</th>
                    <th>ARC</th>
                  </tr>
                </thead>
                <tbody>
                  {report.missions.map((m) => (
                    <tr key={m.missionId}>
                      <td>
                        M{m.module} · {RANK_LABELS[m.rank]} · {missionLabel(m.level)}
                      </td>
                      <td>{m.summary.participants}</td>
                      <td>{pct(m.SCR)}</td>
                      <td>{m.summary.attempts}</td>
                      <td>{m.summary.apiResets}</td>
                      <td>{m.summary.abandoned}</td>
                      <td>{m.summary.combatDeaths}</td>
                      <td>{avg(m.ARC)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>

          <details className="research__details">
            <summary>Participants ({people.length})</summary>
            <div className="research__table-wrap">
              <table className="research__table">
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Attempts</th>
                    <th>Completions</th>
                    <th>Decisions</th>
                    <th>First seen</th>
                    <th>Last seen</th>
                  </tr>
                </thead>
                <tbody>
                  {people.map((p) => (
                    <tr key={p.participant}>
                      <td>{p.participant}</td>
                      <td>{p.attempts}</td>
                      <td>{p.completions}</td>
                      <td>{p.decisions}</td>
                      <td>{p.firstSeen ? new Date(p.firstSeen).toLocaleString() : '—'}</td>
                      <td>{p.lastSeen ? new Date(p.lastSeen).toLocaleString() : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
        </>
      ) : null}
    </div>
  )
}
