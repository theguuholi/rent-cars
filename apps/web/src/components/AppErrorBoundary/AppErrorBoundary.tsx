import { Component, type ErrorInfo, type ReactNode } from 'react'

type Props = { children: ReactNode }
type State = { failed: boolean }

export class AppErrorBoundary extends Component<Props, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    if (import.meta.env.VITE_SENTRY_DSN)
      void import('@sentry/react').then(({ captureException }) =>
        captureException(error, { extra: { componentStack: info.componentStack } }),
      )
  }

  render() {
    if (this.state.failed) {
      return <p role="alert">Something went wrong. Reload the page to try again.</p>
    }
    return this.props.children
  }
}
