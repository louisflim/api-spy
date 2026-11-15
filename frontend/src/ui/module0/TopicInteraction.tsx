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
  const [feedback, setFeedback] = useState<string | null>(null)
  const [ok, setOk] = useState(alreadyComplete)
  const [demoSent, setDemoSent] = useState(alreadyComplete)

  if (interaction.type === 'terminal_training') {
    return (
      <div className="m0-interaction">
        <h3>Interactive exercise</h3>
        <p>{interaction.prompt}</p>
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
              <p className="dash-note">Interaction already completed.</p>
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
        <h3>Interactive exercise</h3>
        <p>{interaction.prompt}</p>
        <pre className="m0-demo-pre">
{`${interaction.demoMethod ?? 'GET'} ${interaction.demoPath ?? ''}

Response ${interaction.demoStatus ?? 200}
${JSON.stringify(interaction.demoBody ?? {}, null, 2)}`}
        </pre>
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
      <div className="m0-options" role="group" aria-label="Answer choices">
        {(interaction.options ?? []).map((option) => (
          <button
            key={option.id}
            type="button"
            className={`m0-option ${selected === option.id ? 'is-selected' : ''}`.trim()}
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
        <ControlGuide message="Unlimited retries. Mistakes are not graded." />
      )}
    </div>
  )
}
