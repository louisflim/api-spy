import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { fetchTerminalVerification, simulateRequest } from '../api/client'
import type {
  ActiveTerminalChallenge,
  MissionDetail,
  MissionProgressState,
  SimulateResponse,
  SimulationMode,
  TerminalVerification,
  VerifyAnotherScenario,
} from '../types/api'
import { conceptLabel } from '../learning/prerequisites'
import { publicErrorMessage } from '../security/publicErrors'
import { recordDeviceUnlock, saveConceptStage, saveFirstAttempt } from '../storage/attemptStorage'
import { ControlGuide } from './spy/ControlGuide'
import { TerminalBootSequence } from './terminal/TerminalBootSequence'
import { TerminalScreenFrame } from './terminal/TerminalScreenFrame'

interface ApiRequestEditorProps {
  participantCode: string
  mission: MissionDetail
  /** MISSION grades first attempts; PRACTICE/TRAINING do not. */
  mode?: SimulationMode
  /** Server-restored active terminal scenario (refresh / reopen). */
  resumeTerminal?: ActiveTerminalChallenge | null
  onClose: () => void
  onSuccess: (payload?: { optionalVerification?: boolean; exitReady?: boolean }) => void
  onAttemptSaved: () => void
  /** Keeps Field pause-menu escalate hints in sync after wrong/right submits. */
  onMissionProgress?: (progress: MissionProgressState) => void
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
  resumeTerminal = null,
  onClose,
  onSuccess,
  onAttemptSaved,
  onMissionProgress,
}: ApiRequestEditorProps) {
  const primary = mission.challenge
  const [active, setActive] = useState<ActiveChallenge>(() => {
    if (resumeTerminal) {
      return toActiveChallenge(mission, {
        challengeId: resumeTerminal.challengeId,
        variantId: resumeTerminal.variantId,
        deviceId: resumeTerminal.deviceId,
        documentation: resumeTerminal.documentation,
        optionalVerification: resumeTerminal.optionalVerification,
      })
    }
    return toActiveChallenge(mission)
  })
  const [reviewQueueNotice, setReviewQueueNotice] = useState<string | null>(null)
  const effectiveMode: SimulationMode = active.practiceMode ? 'PRACTICE' : mode
  const graded = effectiveMode === 'MISSION' && !active.practiceMode
  const allowAnswerPrefill = !graded
  const showTvpShell =
    mode === 'MISSION' &&
    !active.practiceMode &&
    !gradedMissionIsUngraded(mission.mission.missionId)

  const [method, setMethod] = useState(NEUTRAL_METHOD)
  const [path, setPath] = useState(NEUTRAL_PATH)
  const [headersText, setHeadersText] = useState(NEUTRAL_HEADERS)
  const [body, setBody] = useState(NEUTRAL_BODY)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<SimulateResponse | null>(null)
  /** Immediate 0% shell; replaced by server-authoritative snapshot/simulate results. */
  const [tvp, setTvp] = useState<TerminalVerification | null>(() =>
    showTvpShell ? emptyTvpSnapshot(primary.deviceId) : null,
  )
  const [bootComplete, setBootComplete] = useState(false)
  const [progressionNotice, setProgressionNotice] = useState<string | null>(null)
  const [sequenceComplete, setSequenceComplete] = useState(false)
  const advanceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const submitGenerationRef = useRef(0)
  const bootKey = `${active.challengeId}:${active.deviceId}:${
    active.optionalVerification ? 'verify' : active.practiceMode ? 'practice' : 'primary'
  }`
  const gradedMissionId = mission.mission.missionId

  const finishBoot = useCallback(() => setBootComplete(true), [])

  useEffect(() => {
    setBootComplete(false)
  }, [bootKey])

  useEffect(() => {
    return () => {
      if (advanceTimerRef.current) clearTimeout(advanceTimerRef.current)
    }
  }, [])

  // Graded terminals load server-authoritative TVP on open (including 0%). Practice never shows it.
  // Refetch after boot so a transient API blip during open does not leave the bar missing.
  useEffect(() => {
    if (!showTvpShell) {
      setTvp(null)
      return
    }
    setTvp((prev) => prev ?? emptyTvpSnapshot(primary.deviceId))
    let cancelled = false
    let retryTimer: ReturnType<typeof setTimeout> | undefined

    const load = (allowRetry: boolean) => {
      void fetchTerminalVerification(gradedMissionId)
        .then((snapshot) => {
          if (!cancelled) setTvp(snapshot)
        })
        .catch(() => {
          if (cancelled) return
          if (allowRetry) {
            retryTimer = setTimeout(() => load(false), 600)
            return
          }
          // Keep the 0% shell visible; never hide the bar after a fetch blip.
          setTvp((prev) => prev ?? emptyTvpSnapshot(primary.deviceId))
        })
    }

    load(true)
    return () => {
      cancelled = true
      if (retryTimer) clearTimeout(retryTimer)
    }
  }, [showTvpShell, gradedMissionId, bootKey, bootComplete, primary.deviceId])

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
  // Refresh/reopen restores the server-authoritative active terminal scenario.
  useEffect(() => {
    if (resumeTerminal) {
      const next = toActiveChallenge(mission, {
        challengeId: resumeTerminal.challengeId,
        variantId: resumeTerminal.variantId,
        deviceId: resumeTerminal.deviceId,
        documentation: resumeTerminal.documentation,
        optionalVerification: resumeTerminal.optionalVerification,
      })
      setActive(next)
      applyEditorFields(next, mode !== 'MISSION')
      setSequenceComplete(Boolean(resumeTerminal.sequenceComplete))
      setProgressionNotice(
        resumeTerminal.sequenceComplete
          ? 'Verification sequence complete. You can close this terminal and continue the mission — the exit stays unlocked.'
          : null,
      )
      return
    }
    const next = toActiveChallenge(mission)
    setActive(next)
    applyEditorFields(next, mode !== 'MISSION')
    setSequenceComplete(false)
    setProgressionNotice(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    mission.mission.missionId,
    mission.challenge.challengeId,
    mode,
    resumeTerminal?.challengeId,
    resumeTerminal?.sequenceComplete,
    resumeTerminal?.optionalVerification,
  ])

  function startConceptVerification(scenario: VerifyAnotherScenario) {
    const variant = mission.conceptVariants.find((item) => item.challengeId === scenario.challengeId)
    setSequenceComplete(false)
    setProgressionNotice(null)
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

  function scheduleAutoAdvance(scenario: VerifyAnotherScenario, generation: number) {
    if (advanceTimerRef.current) clearTimeout(advanceTimerRef.current)
    setProgressionNotice('Scenario cleared. Loading the next verification scenario…')
    advanceTimerRef.current = setTimeout(() => {
      if (submitGenerationRef.current !== generation) return
      startConceptVerification(scenario)
    }, 1100)
  }

  function markSequenceComplete(generation: number, exitReady?: boolean) {
    if (advanceTimerRef.current) clearTimeout(advanceTimerRef.current)
    if (submitGenerationRef.current !== generation) return
    setSequenceComplete(true)
    setProgressionNotice(
      exitReady
        ? 'All required devices are active. Close this terminal, reach the exit, and press E to complete.'
        : 'This device is clear. Close the terminal and activate any remaining mandatory devices — the exit stays locked until all are done.',
    )
    applyEditorFields(active, false)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!bootComplete || busy || sequenceComplete) return
    if (!method.trim()) {
      setError('Select an HTTP method before sending.')
      return
    }
    if (!path.trim()) {
      setError('Enter a resource path before sending.')
      return
    }

    const generation = ++submitGenerationRef.current
    setBusy(true)
    setError(null)
    setProgressionNotice(null)

    try {
      let headers: Record<string, string> = {}
      if (headersText.trim()) {
        headers = JSON.parse(headersText) as Record<string, string>
      }

      const response = await simulateRequest({
        participantCode,
        missionId: mission.mission.missionId,
        deviceId: active.deviceId,
        challengeId: active.challengeId,
        method,
        path,
        headers,
        body,
        mode: effectiveMode,
      })

      if (submitGenerationRef.current !== generation) return

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

      if (response.conceptReviewQueue && response.conceptReviewQueue.length > 0 && mode !== 'MISSION') {
        const tags = response.conceptReviewQueue.map((item) => conceptLabel(item.conceptTag)).join(', ')
        setReviewQueueNotice(
          `Later review queued for ${tags}. Continue this ungraded drill or return to the mission board.`,
        )
      }
      if (response.missionProgress && onMissionProgress) {
        onMissionProgress(response.missionProgress)
      }

      setResult(response)
      if (showTvpShell && response.terminalVerification) {
        setTvp(response.terminalVerification)
      }
      onAttemptSaved()

      // Graded wrong: stay on the SAME scenario with CONTROL feedback (no auto practice / lesson).
      if (response.correct && !active.practiceMode) {
        onSuccess({
          optionalVerification: Boolean(response.optionalVerification),
          exitReady: Boolean(response.missionProgress?.exitReady),
        })
        const nextScenario = response.terminalVerification?.verifyAnotherScenario ?? null
        const serverActive = response.missionProgress?.activeTerminalChallenge
        const mandatoryChallengeIds = new Set(
          (response.missionProgress?.mandatoryDevices ?? []).map((d) => d.challengeId),
        )
        // Never auto-jump this physical terminal onto another mandatory device's challenge.
        const nextIsOtherMandatory =
          Boolean(nextScenario) &&
          mandatoryChallengeIds.has(nextScenario!.challengeId) &&
          nextScenario!.challengeId !== active.challengeId
        if (
          nextScenario &&
          nextScenario.challengeId !== active.challengeId &&
          !nextIsOtherMandatory
        ) {
          scheduleAutoAdvance(nextScenario, generation)
        } else if (serverActive?.sequenceComplete || (!nextScenario && response.correct) || nextIsOtherMandatory) {
          markSequenceComplete(generation, response.missionProgress?.exitReady)
        }
      }
    } catch (err) {
      if (submitGenerationRef.current === generation) {
        setError(publicErrorMessage(err, 'Request failed. Try again.'))
      }
    } finally {
      if (submitGenerationRef.current === generation) {
        setBusy(false)
      }
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
            {active.optionalVerification ? ' · Concept Verification' : ''}
            {active.practiceMode ? ' · Ungraded Practice' : ''}
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
            unlocks, no RCA first-attempt writes. This does not replace your mandatory mission challenge.
          </p>
        )}
        {active.optionalVerification ? (
          <p className="hint">
            Automatic concept verification: a distinct fictional resource. Eligible Terminal Verification evidence may
            be added. This never re-locks your mandatory mission door or blocks an already unlocked exit.
          </p>
        ) : null}
        {progressionNotice ? (
          <ControlGuide message={progressionNotice} tone="success" />
        ) : null}
        {mode !== 'MISSION' ? (
          <ControlGuide
            message="UNGRADED PRACTICE — different fictional resource. No hearts, TVP, or research first-attempt changes. Graded answers are never revealed."
            tone="brief"
          />
        ) : null}
        {reviewQueueNotice ? <ControlGuide message={reviewQueueNotice} tone="brief" /> : null}

        {showTvpShell && tvp ? (
          <div className="tvp-bar" aria-label="Terminal Verification Progress">
            <div className="tvp-bar__label">
              <span>Terminal Verification</span>
              <span>
                {tvp.requiredSlots > 0
                  ? `${tvp.percent}% · ${tvp.earnedSlots}/${tvp.requiredSlots} evidence slots`
                  : '0% — loading evidence slots…'}
              </span>
            </div>
            <div className="tvp-bar__track">
              <div
                className="tvp-bar__fill"
                style={{ width: `${tvp.requiredSlots > 0 ? Math.max(0, Math.min(100, tvp.percent)) : 0}%` }}
              />
            </div>
            <p className="tvp-bar__note">
              Server-authoritative evidence of eligible correct concept decisions (TVP = floor(100 × earned /
              required)). Not mission completion and not an academic score. Device unlock does not require 100%.
            </p>
          </div>
        ) : null}

        <pre className="docs">{active.documentation}</pre>

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

        <button type="submit" disabled={busy || !bootComplete || sequenceComplete || Boolean(progressionNotice)}>
          {busy ? 'Sending…' : sequenceComplete ? 'Sequence complete' : 'Send simulated request'}
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
                {result.firstAttemptRecorded ? 'recorded now' : 'preserved from earlier'}
                {result.firstAttemptRecord.challengeId
                  ? ` · ${result.firstAttemptRecord.challengeId}`
                  : ''}
                )
              </p>
            ) : null}
            {!result.correct &&
            !result.firstAttemptRecorded &&
            result.firstAttemptRecord.firstAttemptCorrect &&
            graded &&
            !active.practiceMode ? (
              <p className="hint">
                This simulated error does not rewrite your earlier correct first-attempt record for this device, and
                it does not count as a new eligible concept miss for CONTROL support.
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
            {!result.correct && !active.optionalVerification && !active.practiceMode && (
              <p className="hint">Door stays locked. Revise the request. API mistakes never remove hearts.</p>
            )}
            {result.correct &&
              !active.practiceMode &&
              result.terminalVerification &&
              result.terminalVerification.earnedSlots === 0 &&
              !result.firstAttemptRecord.firstAttemptCorrect ? (
              <p className="hint">
                Door unlock is independent of Terminal Verification. Your preserved incorrect first attempt did not
                earn evidence slots; corrected retries never rewrite that record. The next authored verification
                scenario loads automatically when available.
              </p>
            ) : null}
          </div>
        )}
        </form>
      </TerminalScreenFrame>
    </div>
  )
}

function emptyTvpSnapshot(terminalId: string): TerminalVerification {
  return {
    percent: 0,
    earnedSlots: 0,
    requiredSlots: 0,
    terminalId,
    complete: false,
    verifyAnotherScenario: null,
  }
}

function gradedMissionIsUngraded(missionId: string): boolean {
  return missionId.startsWith('practice-') || missionId.startsWith('m0-')
}
