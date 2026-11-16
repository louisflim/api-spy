import { useState } from 'react'
import type { Module0Topic } from '../../content/module0Types'
import { ControlGuide } from '../spy/ControlGuide'

interface TopicInteractionProps {
  topic: Module0Topic
  alreadyComplete: boolean
  onInteractionComplete: () => void
  /** Topic 6 embeds the training room outside this component. */
  onLaunchTerminalTraining?: () => void
  terminalTrainingDone?: boolean
}

export function TopicInteraction({
  topic,
  alreadyComplete,
  onInteractionComplete,
  onLaunchTerminalTraining,
  terminalTrainingDone = false,
}: TopicInteractionProps) {
  const interaction = topic.interaction
  const [selected, setSelected] = useState<string | null>(null)
  const [feedback, setFeedback] = useState<string | null>(
    alreadyComplete ? interaction.successFeedback : null,
  )
  const [ok, setOk] = useState(alreadyComplete)
  const [demoSent, setDemoSent] = useState(alreadyComplete)

  if (interaction.type === 'terminal_training') {
    return (
      <div className="m0-interaction">
        <h3>Interactive exercise · field training</h3>
        <p>{interaction.prompt}</p>
        <p className="m0-visual__lead">
          Educational only — unlimited retries, no graded first attempt, no TVP.
        </p>
        <button type="button" className="dash-btn dash-btn--primary" onClick={onLaunchTerminalTraining}>
          Enter training room
        </button>
        {terminalTrainingDone || alreadyComplete ? (
          <>
            <ControlGuide tone="success" message={interaction.successFeedback} />
            {!alreadyComplete ? (
              <button type="button" className="dash-btn" onClick={onInteractionComplete}>
                Confirm field training complete
              </button>
            ) : (
              <p className="dash-note">Field training already completed. You may revisit anytime.</p>
            )}
          </>
        ) : (
          <ControlGuide message="Walk to the yellow terminal and press E. Use the training path only." />
        )}
      </div>
    )
  }

  if (interaction.type === 'request_response') {
    return (
      <div className="m0-interaction">
        <h3>Interactive exercise · simulated exchange</h3>
        <p>{interaction.prompt}</p>
        <div className="m0-response-card" aria-label="Demo request and response">
          <div className="m0-response-card__status">
            <span>{interaction.demoMethod ?? 'GET'}</span>
            <strong>{interaction.demoPath ?? ''}</strong>
          </div>
          <pre className="m0-demo-pre">
{`Response ${interaction.demoStatus ?? 200}
${JSON.stringify(interaction.demoBody ?? {}, null, 2)}`}
          </pre>
          <ul className="m0-annotation-list">
            <li>
              <strong>Status</strong> — how the fictional server answered
            </li>
            <li>
              <strong>Body</strong> — JSON payload for this training record only
            </li>
          </ul>
        </div>
        <button
          type="button"
          className="dash-btn dash-btn--primary"
          disabled={ok}
          onClick={() => {
            setDemoSent(true)
            setOk(true)
            setFeedback(interaction.successFeedback)
            onInteractionComplete()
          }}
        >
          {demoSent || ok ? 'Demo acknowledged' : 'Send demo request'}
        </button>
        {feedback ? <ControlGuide tone="success" message={feedback} /> : null}
      </div>
    )
  }

  // multi_choice default
  return (
    <div className="m0-interaction">
      <h3>Interactive exercise</h3>
      <p>{interaction.prompt}</p>
      <p className="m0-visual__lead">Unlimited retries. Mistakes are not graded and never affect RCA/SCR/HLCR.</p>
      <div className="m0-options" role="group" aria-label="Answer choices">
        {(interaction.options ?? []).map((option) => (
          <button
            key={option.id}
            type="button"
            className={`m0-option ${selected === option.id ? 'is-selected' : ''} ${
              ok && alreadyComplete && option.id === interaction.correctOptionId ? 'is-correct' : ''
            }`.trim()}
            disabled={ok && alreadyComplete}
            onClick={() => setSelected(option.id)}
          >
            {option.label}
          </button>
        ))}
      </div>
      <button
        type="button"
        className="dash-btn dash-btn--primary"
        disabled={!selected || (ok && alreadyComplete)}
        onClick={() => {
          if (!selected) return
          if (selected === interaction.correctOptionId) {
            setOk(true)
            setFeedback(interaction.successFeedback)
            onInteractionComplete()
          } else {
            setOk(false)
            setFeedback(interaction.failureFeedback)
          }
        }}
      >
        Check answer
      </button>
      {feedback ? (
        <ControlGuide tone={ok ? 'success' : 'warn'} message={feedback} />
      ) : (
        <ControlGuide message="Review the briefing above, then choose the best statement." />
      )}
    </div>
  )
}
