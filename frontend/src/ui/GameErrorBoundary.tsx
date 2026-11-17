import { Component } from 'react'
import type { ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  message: string | null
}

export class GameErrorBoundary extends Component<Props, State> {
  state: State = { message: null }

  static getDerivedStateFromError(error: Error): State {
    // Never surface raw exception text in production builds.
    const message = import.meta.env.DEV
      ? error.message
      : 'Something went wrong. Reload and try again.'
    return { message }
  }

  render() {
    if (this.state.message) {
      return (
        <div className="game-shell" style={{ display: 'grid', placeItems: 'center', color: '#ff7b7b' }}>
          Game failed to start: {this.state.message}
        </div>
      )
    }
    return this.props.children
  }
}
