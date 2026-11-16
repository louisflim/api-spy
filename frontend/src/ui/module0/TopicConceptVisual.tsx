import { useState } from 'react'
import type { Module0Topic } from '../../content/module0Types'

interface TopicConceptVisualProps {
  topic: Module0Topic
}

const METHOD_CARDS = [
  {
    id: 'GET',
    title: 'GET',
    summary: 'Retrieve an existing resource without changing it.',
    example: 'GET /api/training/briefings/intro',
  },
  {
    id: 'POST',
    title: 'POST',
    summary: 'Submit or create something new for a collection.',
    example: 'POST /api/training/notes',
  },
  {
    id: 'PUT',
    title: 'PUT',
    summary: 'Replace an existing resource identified in the path.',
    example: 'PUT /api/training/lockers/sample/schedule',
  },
  {
    id: 'DELETE',
    title: 'DELETE',
    summary: 'Remove or deactivate an existing resource.',
    example: 'DELETE /api/training/notes/temp-1',
  },
] as const

/**
 * Authored visual teaching aids for Module 0 — educational only, not graded.
 */
export function TopicConceptVisual({ topic }: TopicConceptVisualProps) {
  if (topic.id === 'http-methods') {
    return <MethodCards />
  }
  if (topic.id === 'endpoints') {
    return <EndpointInspector />
  }
  if (topic.id === 'parameters-responses') {
    return <ParametersAndResponseDemo />
  }
  if (topic.id === 'understanding-apis') {
    return <RequestResponseDiagram />
  }
  if (topic.id === 'terminal-field') {
    return <TerminalAnatomy />
  }
  if (topic.id === 'welcome') {
    return <AgencyMapHint />
  }
  return null
}

function MethodCards() {
  const [active, setActive] = useState<(typeof METHOD_CARDS)[number]['id']>('GET')
  const card = METHOD_CARDS.find((item) => item.id === active) ?? METHOD_CARDS[0]

  return (
    <section className="m0-visual" aria-label="HTTP method cards">
      <h3>Method cards</h3>
      <p className="m0-visual__lead">
        Select a method to review its job. These examples are training-only fiction.
      </p>
      <div className="m0-method-grid" role="tablist" aria-label="HTTP methods">
        {METHOD_CARDS.map((item) => (
          <button
            key={item.id}
            type="button"
            role="tab"
            aria-selected={active === item.id}
            className={`m0-method-card ${active === item.id ? 'is-active' : ''}`.trim()}
            onClick={() => setActive(item.id)}
          >
            <strong>{item.title}</strong>
            <span>{item.summary}</span>
          </button>
        ))}
      </div>
      <div className="m0-visual__panel" role="tabpanel">
        <p>
          <strong>{card.title}</strong> — {card.summary}
        </p>
        <pre className="m0-example-pre">{card.example}</pre>
      </div>
    </section>
  )
}

function EndpointInspector() {
  const [focus, setFocus] = useState<'api' | 'collection' | 'id'>('collection')
  const notes = {
    api: 'Common fictional API prefix used by training contracts.',
    collection: 'Names the resource type — here, training lockers.',
    id: 'Identifies one locker in the collection: academy.',
  } as const

  return (
    <section className="m0-visual" aria-label="Endpoint inspector">
      <h3>Inspect a training endpoint</h3>
      <p className="m0-visual__lead">
        Click each path segment. Mission doors use different paths — never confuse them with training.
      </p>
      <div className="m0-endpoint" role="group" aria-label="Fictional endpoint parts">
        <span className="m0-endpoint__host">hq.local</span>
        <button
          type="button"
          className={`m0-endpoint__part ${focus === 'api' ? 'is-active' : ''}`.trim()}
          onClick={() => setFocus('api')}
        >
          /api
        </button>
        <button
          type="button"
          className={`m0-endpoint__part ${focus === 'collection' ? 'is-active' : ''}`.trim()}
          onClick={() => setFocus('collection')}
        >
          /training/lockers
        </button>
        <button
          type="button"
          className={`m0-endpoint__part ${focus === 'id' ? 'is-active' : ''}`.trim()}
          onClick={() => setFocus('id')}
        >
          /academy
        </button>
      </div>
      <div className="m0-visual__panel">
        <p>{notes[focus]}</p>
        <pre className="m0-example-pre">Full path: /api/training/lockers/academy</pre>
      </div>
    </section>
  )
}

function ParametersAndResponseDemo() {
  const [mode, setMode] = useState<'path' | 'query'>('path')

  return (
    <section className="m0-visual" aria-label="Parameters and responses">
      <h3>Path vs query · annotated response</h3>
      <div className="m0-toggle-row" role="group" aria-label="Parameter style">
        <button
          type="button"
          className={`m0-toggle ${mode === 'path' ? 'is-active' : ''}`.trim()}
          onClick={() => setMode('path')}
        >
          Path parameter
        </button>
        <button
          type="button"
          className={`m0-toggle ${mode === 'query' ? 'is-active' : ''}`.trim()}
          onClick={() => setMode('query')}
        >
          Query parameter
        </button>
      </div>
      <div className="m0-visual__panel">
        {mode === 'path' ? (
          <>
            <p>
              Path parameters sit <em>inside</em> the path and identify one item.
            </p>
            <pre className="m0-example-pre">
{`Template: /api/training/crates/{crateId}
Request:  /api/training/crates/crate-42
                 └─ path value replaces {crateId}`}
            </pre>
          </>
        ) : (
          <>
            <p>
              Query parameters appear after <code>?</code> and filter or configure a request.
            </p>
            <pre className="m0-example-pre">
{`Request: /api/training/signals?channel=green
                              └─ query name=value`}
            </pre>
          </>
        )}
      </div>
      <div className="m0-response-card" aria-label="Simulated response">
        <div className="m0-response-card__status">
          <span>200</span>
          <strong>OK</strong>
          <em>Success — contract matched</em>
        </div>
        <pre className="m0-example-pre">
{`{
  "ok": true,
  "detail": "Training endpoint responded successfully."
}`}
        </pre>
        <ul className="m0-annotation-list">
          <li>
            <strong>200</strong> — request matched the fictional contract
          </li>
          <li>
            <strong>400</strong> — method, path, or parameters did not match
          </li>
          <li>
            <strong>404</strong> — resource path not found in the contract
          </li>
        </ul>
      </div>
    </section>
  )
}

function RequestResponseDiagram() {
  return (
    <section className="m0-visual" aria-label="Request response flow">
      <h3>Request → response loop</h3>
      <div className="m0-flow" aria-hidden="true">
        <div className="m0-flow__node">
          <span>Agent terminal</span>
          <strong>Request</strong>
          <code>GET /api/training/records/demo</code>
        </div>
        <div className="m0-flow__arrow">→</div>
        <div className="m0-flow__node">
          <span>Fictional desk</span>
          <strong>Response</strong>
          <code>200 + JSON body</code>
        </div>
      </div>
      <p className="m0-visual__lead">
        Nothing you type leaves this training network. Live missions use different paths.
      </p>
    </section>
  )
}

function TerminalAnatomy() {
  return (
    <section className="m0-visual" aria-label="Terminal field anatomy">
      <h3>Where each field goes</h3>
      <ol className="m0-anatomy">
        <li>
          <strong>Method</strong> — choose GET for this training locker retrieval
        </li>
        <li>
          <strong>Path</strong> — enter <code>/api/training/lockers/academy</code>
        </li>
        <li>
          <strong>Headers</strong> — optional JSON object (often Accept)
        </li>
        <li>
          <strong>Body</strong> — leave empty for this GET drill
        </li>
      </ol>
      <p className="m0-visual__lead">
        Walk to the yellow terminal, press E, then send the request. Unlimited retries. No TVP or RCA.
      </p>
    </section>
  )
}

function AgencyMapHint() {
  return (
    <section className="m0-visual" aria-label="Agency orientation map">
      <h3>How HQ fits together</h3>
      <div className="m0-map-grid">
        <div className="m0-map-tile">
          <strong>Module 0</strong>
          <span>Ungraded orientation</span>
        </div>
        <div className="m0-map-tile">
          <strong>Ops Board</strong>
          <span>Independent missions</span>
        </div>
        <div className="m0-map-tile">
          <strong>Field room</strong>
          <span>Terminals + exit</span>
        </div>
      </div>
      <p className="m0-visual__lead">
        Controls: WASD / arrows to walk · E to open a terminal · API mistakes never remove hearts.
      </p>
    </section>
  )
}
