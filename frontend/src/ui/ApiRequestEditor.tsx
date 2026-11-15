import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { fetchTerminalVerification, simulateRequest } from '../api/client'
import type {
  AdaptivePracticeDoor,
  MissionDetail,
  SimulateResponse,
  SimulationMode,
  TerminalVerification,
  VerifyAnotherScenario,
} from '../types/api'
import { recordDeviceUnlock, saveConceptStage, saveFirstAttempt } from '../storage/attemptStorage'
import { ControlGuide } from './spy/ControlGuide'
import { TerminalBootSequence } from './terminal/TerminalBootSequence'
import { TerminalScreenFrame } from './terminal/TerminalScreenFrame'

interface ApiRequestEditorProps {
  participantCode: string
  mission: MissionDetail
  /** MISSION grades first attempts; PRACTICE/TRAINING do not. */
  mode?: SimulationMode
  onClose: () => void
  onSuccess: (payload?: { optionalVerification?: boolean; exitReady?: boolean }) => void
  onAttemptSaved: () => void
  onAdaptivePracticeDoors?: (doors: AdaptivePracticeDoor[]) => void
}

const NEUTRAL_METHOD = ''
const NEUTRAL_PATH = ''
const NEUTRAL_HEADERS = '{\n}'
const NEUTRAL_BODY = ''

function describeErrorCode(code: string): string {
  switch (code) {
    case 'METHOD':
      return 'METHOD — HTTP method does not match the documented contract (http.method.get).'
    case 'ENDPOINT':
      return 'ENDPOINT — Resource path does not match the documented endpoint (http.endpoint).'
    case 'PARAMETER_MISSING':
      return 'PARAMETER_MISSING — A required path or query parameter is missing.'
    case 'PARAMETER_VALUE':
      return 'PARAMETER_VALUE — A required parameter value is incorrect.'
    case 'PARAMETER':
      return 'PARAMETER — Parameter usage does not match the fictional contract.'
    default:
      return code
  }
}

type ActiveChallenge = {
  challengeId: string
  variantId: string
  deviceId: string
  conceptTags: string[]
  documentation: string
  /** Guided prefill only for ungraded practice/training. */
  expectedMethod?: string
  pathTemplate?: string
  pathParameters?: Record<string, string>
  queryParameters?: Record<string, string>
  optionalVerification: boolean
  practiceMode: boolean
}

function toActiveChallenge(
  mission: MissionDetail,
  overrides?: Partial<ActiveChallenge>,
): ActiveChallenge {
  const primary = mission.challenge
  return {
    challengeId: primary.challengeId,
    variantId: primary.variantId,
    deviceId: primary.deviceId,
    conceptTags: primary.conceptTags,
    documentation: primary.documentation,
    expectedMethod: primary.expectedMethod,
    pathTemplate: primary.pathTemplate,
    pathParameters: primary.pathParameters ?? {},
    queryParameters: primary.queryParameters ?? {},
    optionalVerification: false,
    practiceMode: false,
    ...overrides,
  }
}

function guidedPrefillPath(challenge: ActiveChallenge): string {
  const pathTemplate = challenge.pathTemplate ?? ''
  const queryParameters = challenge.queryParameters ?? {}
  const pathParameters = challenge.pathParameters ?? {}
  if (Object.keys(queryParameters).length > 0) {
    const query = new URLSearchParams(queryParameters).toString()
    return `${pathTemplate}?${query}`
  }
  if (Object.keys(pathParameters).length > 0) {
    let path = pathTemplate
    for (const [key, value] of Object.entries(pathParameters)) {
      path = path.replace(`{${key}}`, value)
    }
    return path
  }
  return pathTemplate
}

export function ApiRequestEditor({
  participantCode,
  mission,
  mode = 'MISSION',
  onClose,
  onSuccess,
  onAttemptSaved,
  onAdaptivePracticeDoors,
}: ApiRequestEditorProps) {
  const primary = mission.challenge
  const [active, setActive] = useState<ActiveChallenge>(() => toActiveChallenge(mission))
  const effectiveMode: SimulationMode = active.practiceMode ? 'PRACTICE' : mode
  const graded = effectiveMode === 'MISSION' && !active.practiceMode
  const allowAnswerPrefill = !graded

  const [method, setMethod] = useState(NEUTRAL_METHOD)
  const [path, setPath] = useState(NEUTRAL_PATH)
  const [headersText, setHeadersText] = useState(NEUTRAL_HEADERS)
  const [body, setBody] = useState(NEUTRAL_BODY)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<SimulateResponse | null>(null)
  const [tvp, setTvp] = useState<TerminalVerification | null>(null)
  const [bootComplete, setBootComplete] = useState(false)
  const bootKey = `${active.challengeId}:${active.deviceId}:${active.optionalVerification ? 'verify' : 'primary'}`
  const gradedMissionId = mission.mission.missionId

  const finishBoot = useCallback(() => setBootComplete(true), [])

  useEffect(() => {
    setBootComplete(false)
  }, [bootKey])

  // Graded terminals load server-authoritative TVP on open (including 0%). Practice never shows it.
  useEffect(() => {
    if (!graded || active.practiceMode) {
      setTvp(null)
      return
    }
    if (gradedMissionId.startsWith('practice-') || gradedMissionId.startsWith('m0-')) {
      setTvp(null)
      return
    }
    let cancelled = false
    void fetchTerminalVerification(gradedMissionId)
      .then((snapshot) => {
        if (!cancelled) setTvp(snapshot)
      })
      .catch(() => {
        if (!cancelled) setTvp(null)
      })
    return () => {
      cancelled = true
    }
  }, [graded, active.practiceMode, gradedMissionId, bootKey])

  function applyEditorFields(next: ActiveChallenge, guided: boolean) {
    const canGuide = guided && Boolean(next.expectedMethod && next.pathTemplate)
    if (canGuide) {
      setMethod(next.expectedMethod || 'GET')
      setPath(guidedPrefillPath(next))
      setHeadersText('{\n  "Accept": "application/json"\n}')
      setBody('')
    } else {
      setMethod(NEUTRAL_METHOD)
      setPath(NEUTRAL_PATH)
      setHeadersText(NEUTRAL_HEADERS)
      setBody(NEUTRAL_BODY)
    }
    setResult(null)
    setError(null)
  }

  function loadChallenge(next: ActiveChallenge, guided: boolean) {
    setActive(next)
    applyEditorFields(next, guided)
  }

  // Mission / challenge identity change must never leave a prior correct request prefilled.
  useEffect(() => {
    const next = toActiveChallenge(mission)
    setActive(next)
    applyEditorFields(next, mode !== 'MISSION')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mission.mission.missionId, mission.challenge.challengeId, mode])

  function startVerifyAnother(scenario: VerifyAnotherScenario) {
    const variant = mission.conceptVariants.find((item) => item.challengeId === scenario.challengeId)
    loadChallenge(
      {
        challengeId: scenario.challengeId,
        variantId: scenario.variantId,
        deviceId: scenario.deviceId,
        conceptTags: variant?.conceptTags ?? primary.conceptTags,
        documentation: scenario.documentation,
        optionalVerification: true,
        practiceMode: false,
      },
      false,
    )
  }

  function startPracticeDoor(door: AdaptivePracticeDoor) {
    loadChallenge(
      {
        challengeId: door.challengeId,
        variantId: door.challengeId,
        deviceId: door.deviceId,
        conceptTags: [door.conceptTag],
        documentation: door.documentation,
        expectedMethod: door.expectedMethod,
        pathTemplate: door.pathTemplate,
        pathParameters: {},
        queryParameters: {},
        optionalVerification: false,
        practiceMode: true,
      },
      true,
    )
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!bootComplete || busy) return
    if (!method.trim()) {
      setError('Select an HTTP method before sending.')
      return
    }
    if (!path.trim()) {
      setError('Enter a resource path before sending.')
      return
    }

    setBusy(true)
    setError(null)

    try {
      let headers: Record<string, string> = {}
      if (headersText.trim()) {
        headers = JSON.parse(headersText) as Record<string, string>
      }

      const response = await simulateRequest({
        participantCode,
        missionId: active.practiceMode
          ? doorMissionId(active.challengeId)
          : mission.mission.missionId,
        deviceId: active.deviceId,
        challengeId: active.challengeId,
        method,
        path,
        headers,
        body,
        mode: effectiveMode,
      })

      const record = response.firstAttemptRecord
      if (graded && !response.optionalVerification) {
        saveFirstAttempt({
          participantCode: record.participantCode,
          missionId: record.missionId,
          deviceId: record.deviceId,
          challengeId: record.challengeId,
          variantId: record.variantId,
          conceptTags: record.conceptTags,
          method: record.method,
          path: record.path,
          headers: record.headers,
          body: record.body,
          errorCodes: record.errorCodes,
          firstAttemptCorrect: record.firstAttemptCorrect,
          conceptFirstAttemptCorrect: record.conceptFirstAttemptCorrect,
          submittedAt: record.submittedAt,
          simulatedStatus: response.simulatedResponse.status,
          doorUnlocked: response.correct && response.deviceChanges.some((change) => change.unlocked),
        })
      }

      for (const conceptTag of response.conceptTags) {
        saveConceptStage(participantCode, conceptTag, 'PRACTICING')
        if (
          graded &&
          !response.optionalVerification &&
          record.conceptFirstAttemptCorrect[conceptTag] === false &&
          response.correct
        ) {
          saveConceptStage(participantCode, conceptTag, 'IMPROVING')
        }
      }

      if (
        graded &&
        response.correct &&
        !response.optionalVerification &&
        !active.practiceMode &&
        response.missionProgress
      ) {
        recordDeviceUnlock({
          participantCode,
          missionId: mission.mission.missionId,
          deviceId: active.deviceId,
          requiredDeviceIds: response.missionProgress.requiredDeviceIds,
        })
      }

      if (response.adaptivePracticeDoors && onAdaptivePracticeDoors) {
        onAdaptivePracticeDoors(response.adaptivePracticeDoors)
      }

      setResult(response)
      if (graded && response.terminalVerification) {
        setTvp(response.terminalVerification)
      }
      onAttemptSaved()
      if (response.correct && !active.practiceMode) {
        onSuccess({
          optionalVerification: Boolean(response.optionalVerification),
          exitReady: Boolean(response.missionProgress?.exitReady),
        })
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Request failed')
    } finally {
      setBusy(false)
    }
  }

  const controlMessage =
    result?.controlFeedback?.messages?.join(' ') ||
    (result && !result.correct
      ? 'Agent, revise the request against the terminal documentation.'
      : '')

  const modeLabel = active.practiceMode
    ? 'PRACTICE'
    : active.optionalVerification
      ? 'VERIFY'
      : graded
        ? 'MISSION'
        : mode
  const consoleTitle =
    mode === 'PRACTICE' || active.practiceMode
      ? 'Practice Terminal'
      : mode === 'TRAINING'
        ? 'Training Terminal'
        : 'API Terminal'

  return (
    <div
      className="editor-overlay terminal-overlay"
      role="dialog"
      aria-modal="true"
      aria-label="API terminal"
    >
      <TerminalScreenFrame
        title={consoleTitle}
        subtitle="Secure Access Console"
        statusLabel={bootComplete ? 'System online' : 'Booting…'}
        powered={bootComplete}
        onClose={onClose}
      >
        {!bootComplete ? (
          <TerminalBootSequence key={bootKey} onComplete={finishBoot} />
        ) : null}
        <form
          className={`editor-panel ${bootComplete ? 'is-ready' : 'is-booting'}`}
          onSubmit={handleSubmit}
          inert={!bootComplete || undefined}
          aria-hidden={!bootComplete}
        >
        <div className="editor-header">
          <h2>
            Field brief · {modeLabel}
            {active.optionalVerification ? ' · Verify Another Scenario' : ''}
          </h2>
          <button type="button" className="ghost" onClick={onClose}>
            Close
          </button>
        </div>
        {graded ? (
          <p className="hint">
            Graded mission terminal: construct method and path from the field brief. Inputs are not prefilled with
            the solution.
          </p>
        ) : (
          <p className="hint">
            Educational {effectiveMode.toLowerCase()} mode: unlimited retries, guided fields allowed, no mission door
            unlocks, no RCA first-attempt writes.
          </p>
        )}
        {active.optionalVerification ? (
          <p className="hint">
            Optional verification scenario. Completing it does not block the exit or grant mission rewards.
          </p>
        ) : null}

        <pre className="docs">{active.documentation}</pre>

        {tvp ? (
          <div className="tvp-bar" aria-label="Terminal Verification Progress">
            <div className="tvp-bar__label">
              <span>Terminal Verification</span>
              <span>
                {tvp.percent}% · {tvp.earnedSlots}/{tvp.requiredSlots} evidence slots
              </span>
            </div>
            <div className="tvp-bar__track">
              <div className="tvp-bar__fill" style={{ width: `${tvp.percent}%` }} />
            </div>
            <p className="tvp-bar__note">
              Measures verified API decisions on this terminal — not mission completion.
            </p>
          </div>
        ) : null}

        {controlMessage ? (
          <ControlGuide
            message={controlMessage}
            tone={
              result?.controlFeedback?.tone === 'success'
                ? 'success'
                : result?.controlFeedback?.tone === 'warn'
                  ? 'warn'
                  : 'brief'
            }
          />
        ) : null}

        <label>
          Method
          <select value={method} onChange={(e) => setMethod(e.target.value)} required>
            {!allowAnswerPrefill || method === NEUTRAL_METHOD ? (
              <option value="" disabled>
                Select method…
              </option>
            ) : null}
            <option value="GET">GET</option>
            <option value="POST">POST</option>
            <option value="PUT">PUT</option>
            <option value="PATCH">PATCH</option>
            <option value="DELETE">DELETE</option>
          </select>
        </label>

        <label>
          Path (include query string when required)
          <input
            value={path}
            onChange={(e) => setPath(e.target.value)}
            spellCheck={false}
            placeholder="/api/…/{resourceId} or /api/…?name=value"
            autoComplete="off"
          />
        </label>

        <label>
          Headers (JSON object)
          <textarea
            value={headersText}
            onChange={(e) => setHeadersText(e.target.value)}
            rows={4}
            spellCheck={false}
            placeholder="{}"
          />
        </label>

        <label>
          Body
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            rows={4}
            spellCheck={false}
            placeholder="Leave empty unless the brief requires a body"
          />
        </label>

        <button type="submit" disabled={busy || !bootComplete}>
          {busy ? 'Sending…' : 'Send simulated request'}
        </button>

        {error && <p className="error">{error}</p>}

        {result && (
          <div className="response-panel">
            <h3>Simulated HTTP response</h3>
            <p>
              Status: <strong>{result.simulatedResponse.status}</strong>
              {result.heartsChanged ? ' (unexpected heart change)' : ' · hearts unchanged'}
            </p>
            <p className="hint">
              Read the status and JSON body before revising. This response is simulated fiction — never forwarded
              to a real external API.
            </p>
            {result.errorCodes.length > 0 && (
              <div className="hint">
                <p>Detected concept issues (multiple can appear together):</p>
                <ul>
                  {result.errorCodes.map((code) => (
                    <li key={code}>{describeErrorCode(code)}</li>
                  ))}
                </ul>
              </div>
            )}
            <pre>{JSON.stringify(result.simulatedResponse.body, null, 2)}</pre>
            {graded && !active.practiceMode ? (
              <p className="hint">
                First attempt immutable:{' '}
                {result.firstAttemptRecord.firstAttemptCorrect ? 'correct' : 'incorrect'} (
                {result.firstAttemptRecorded ? 'recorded now' : 'preserved from earlier'})
              </p>
            ) : null}
            {Object.keys(result.firstAttemptRecord.conceptFirstAttemptCorrect).length > 0 && (
              <p className="hint">
                Concept first-attempt map:{' '}
                {Object.entries(result.firstAttemptRecord.conceptFirstAttemptCorrect)
                  .map(([tag, ok]) => `${tag}=${ok ? 'ok' : 'miss'}`)
                  .join(', ')}
              </p>
            )}
            {!result.correct && !active.optionalVerification && (
              <p className="hint">Door stays locked. Revise the request. API mistakes never remove hearts.</p>
            )}
            {result.correct &&
              result.terminalVerification &&
              !result.terminalVerification.complete &&
              result.terminalVerification.verifyAnotherScenario &&
              !active.optionalVerification && (
                <button
                  type="button"
                  className="open-terminal"
                  onClick={() =>
                    startVerifyAnother(result.terminalVerification!.verifyAnotherScenario!)
                  }
                >
                  Verify Another Scenario
                </button>
              )}
            {result.adaptivePracticeDoors && result.adaptivePracticeDoors.length > 0 ? (
              <div className="hint">
                <p>Adaptive practice doors available in this room (max 2):</p>
                <ul>
                  {result.adaptivePracticeDoors.map((door) => (
                    <li key={`${door.challengeId}-${door.conceptTag}`}>
                      <button type="button" className="ghost" onClick={() => startPracticeDoor(door)}>
                        Open {door.label}
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
          </div>
        )}
        </form>
      </TerminalScreenFrame>
    </div>
  )
}

function doorMissionId(challengeId: string): string {
  if (challengeId.includes('l2')) return 'practice-m1-recruit-l2'
  if (challengeId.includes('l3')) return 'practice-m1-recruit-l3'
  return 'practice-m1-recruit-l1'
}
